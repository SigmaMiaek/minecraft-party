package pl.minecraftparty.core.upnp;

import pl.minecraftparty.core.InterfaceSelection;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** IGD v1/v2 TCP mapping with a finite lease, ownership checks and best-effort cleanup. No relay. */
public final class UpnpPortMapper {
    private static final int LEASE_SECONDS = 1800;
    private UpnpPortMapper() { }
    public static Mapping open(int internalPort, BooleanSupplier cancelled, Consumer<String> lost) throws IOException {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER)
                .proxy(new ProxySelector() {
                    public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                    public void connectFailed(URI uri, SocketAddress address, IOException error) { }
                }).build();
        try {
            for (URI description : discover(cancelled)) {
                check(cancelled);
                List<UpnpProtocol.Gateway> gateways;
                try {
                    Reply document = read(http, HttpRequest.newBuilder(description).timeout(Duration.ofSeconds(3)).GET().build());
                    if (document.status != 200) continue;
                    gateways = UpnpProtocol.gateways(description, document.body);
                }
                catch (IOException e) { continue; }
                for (var gateway : gateways) {
                    check(cancelled);
                    try {
                        String external = UpnpProtocol.value(soap(http, gateway, "GetExternalIPAddress", Map.of()), "NewExternalIPAddress");
                        if (!PublicAddress.ipv4(external)) continue;
                        String local;
                        try (DatagramSocket route = new DatagramSocket()) {
                            route.connect(InetAddress.getByName(gateway.control().getHost()), 1900);
                            local = route.getLocalAddress().getHostAddress();
                        }
                        String owner = "MinecraftParty-" + UUID.randomUUID();
                        for (int attempt = 0; attempt < 3; attempt++) {
                            check(cancelled);
                            int externalPort = attempt == 0 ? internalPort : new SecureRandom().nextInt(49152, 65536);
                            Mapping mapping = new Mapping(http, gateway, external, local, internalPort, externalPort, owner, lost);
                            // A successful lookup means another mapping exists. Do not overwrite it.
                            try { mapping.entry(); continue; }
                            catch (SoapFault e) { if (e.code != 714) continue; }
                            mapping.add();
                            try { mapping.requireReady(); }
                            catch (IOException e) { mapping.removeIfOwned(); throw e; }
                            if (cancelled.getAsBoolean()) { mapping.removeIfOwned(); throw new IOException("cancelled"); }
                            mapping.start(); return mapping;
                        }
                    } catch (IOException e) { if (cancelled.getAsBoolean()) throw e; }
                }
            }
            throw new IOException("No public IPv4 with temporary UPnP mapping; CGNAT or gateway policy may block P2P");
        } catch (IOException | RuntimeException e) { http.close(); throw e; }
    }
    private static List<URI> discover(BooleanSupplier cancelled) throws IOException {
        Set<URI> found = new LinkedHashSet<>(); int interfaces = 0;
        var networks = NetworkInterface.getNetworkInterfaces();
        while (networks.hasMoreElements() && interfaces < 4 && found.size() < 4) {
            var network = networks.nextElement();
            if (!network.isUp() || network.isLoopback() || !network.supportsMulticast()) continue;
            if (!InterfaceSelection.eligible(network.getName(), network.getDisplayName(), network.isPointToPoint(), network.isVirtual())) continue;
            Inet4Address local = network.inetAddresses().filter(a -> a instanceof Inet4Address && a.isSiteLocalAddress())
                    .map(a -> (Inet4Address) a).findFirst().orElse(null);
            if (local == null) continue;
            interfaces++; check(cancelled);
            try (MulticastSocket socket = new MulticastSocket(null)) {
                socket.bind(new InetSocketAddress(local, 0)); socket.setNetworkInterface(network); socket.setTimeToLive(2);
                socket.setSoTimeout(300);
                for (String target : List.of("urn:schemas-upnp-org:device:InternetGatewayDevice:1", "urn:schemas-upnp-org:device:InternetGatewayDevice:2")) {
                    byte[] request = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: " + target + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                    socket.send(new DatagramPacket(request, request.length, InetAddress.getByName("239.255.255.250"), 1900));
                }
                long until = System.nanoTime() + 1_500_000_000L;
                while (System.nanoTime() < until && found.size() < 4) {
                    check(cancelled);
                    byte[] buffer = new byte[8193]; DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                        URI location = UpnpProtocol.location(new String(buffer, 0, packet.getLength(), StandardCharsets.US_ASCII), packet.getAddress());
                        found.add(location);
                    } catch (SocketTimeoutException ignored) { }
                    catch (IOException ignored) { }
                }
            } catch (IOException e) { if (cancelled.getAsBoolean()) throw e; }
        }
        return List.copyOf(found);
    }
    private static void check(BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new IOException("cancelled");
    }
    private static byte[] soap(HttpClient http, UpnpProtocol.Gateway gateway, String action, Map<String, String> arguments) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(gateway.control()).timeout(Duration.ofSeconds(3))
                .header("Content-Type", "text/xml; charset=utf-8").header("SOAPAction", "\"" + gateway.service() + "#" + action + "\"")
                .POST(HttpRequest.BodyPublishers.ofByteArray(UpnpProtocol.request(gateway.service(), action, arguments))).build();
        Reply reply = read(http, request);
        if (reply.status != 200) {
            int code;
            try { code = Integer.parseInt(UpnpProtocol.value(reply.body, "errorCode")); }
            catch (IOException | NumberFormatException e) { throw new IOException("gateway request rejected"); }
            throw new SoapFault(code);
        }
        return reply.body;
    }
    private static Reply read(HttpClient http, HttpRequest request) throws IOException {
        try {
            HttpResponse<byte[]> response = http.send(request, info -> new LimitedBody());
            return new Reply(response.statusCode(), response.body());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("cancelled", e); }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> future = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return future; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int count = buffer.remaining();
                if (count > UpnpProtocol.MAX_XML - bytes.size()) {
                    subscription.cancel(); future.completeExceptionally(new IOException("gateway response too large")); return;
                }
                byte[] chunk = new byte[count]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { future.completeExceptionally(error); }
        public void onComplete() { future.complete(bytes.toByteArray()); }
    }
    private record Reply(int status, byte[] body) { }
    private static final class SoapFault extends IOException {
        final int code;
        SoapFault(int code) { super("UPnP error " + code); this.code = code; }
    }
    public static final class Mapping implements AutoCloseable {
        private final HttpClient http;
        private final UpnpProtocol.Gateway gateway;
        private final String address, internalAddress, owner;
        private final int internalPort, externalPort;
        private final Consumer<String> lost;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile boolean valid = true;
        private Thread renewer;
        private Mapping(HttpClient http, UpnpProtocol.Gateway gateway, String address, String internalAddress,
                        int internalPort, int externalPort, String owner, Consumer<String> lost) {
            this.http = http; this.gateway = gateway; this.address = address; this.internalAddress = internalAddress;
            this.internalPort = internalPort; this.externalPort = externalPort; this.owner = owner; this.lost = lost;
        }
        public String address() { return address; }
        public int port() { return externalPort; }
        public boolean valid() { return valid && !closed.get(); }
        private Map<String, String> key() {
            Map<String, String> args = new LinkedHashMap<>();
            args.put("NewRemoteHost", ""); args.put("NewExternalPort", Integer.toString(externalPort)); args.put("NewProtocol", "TCP"); return args;
        }
        private byte[] entry() throws IOException { return soap(http, gateway, "GetSpecificPortMappingEntry", key()); }
        private void requireOwnership() throws IOException {
            if (!UpnpProtocol.mappingOwned(entry(), internalAddress, internalPort, owner))
                throw new IOException("port mapping belongs to another application");
        }
        private void requireReady() throws IOException {
            byte[] entry = entry();
            if (!UpnpProtocol.mappingOwned(entry, internalAddress, internalPort, owner)
                    || !UpnpProtocol.temporaryMapping(entry, LEASE_SECONDS * 2)) throw new IOException("temporary mapping not confirmed");
        }
        private void add() throws IOException {
            Map<String, String> args = key();
            args.put("NewInternalPort", Integer.toString(internalPort)); args.put("NewInternalClient", internalAddress);
            args.put("NewEnabled", "1"); args.put("NewPortMappingDescription", owner); args.put("NewLeaseDuration", Integer.toString(LEASE_SECONDS));
            soap(http, gateway, "AddPortMapping", args);
        }
        private void start() {
            renewer = Thread.ofVirtual().name("party-upnp-renew").start(() -> {
                try {
                    while (!closed.get()) {
                        Thread.sleep(LEASE_SECONDS * 500L);
                        if (closed.get()) return;
                        requireOwnership(); add(); requireReady();
                        String current = UpnpProtocol.value(soap(http, gateway, "GetExternalIPAddress", Map.of()), "NewExternalIPAddress");
                        if (!address.equals(current)) throw new IOException("public address changed");
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (IOException e) { valid = false; if (!closed.get()) lost.accept("internet_lost"); }
            });
        }
        private void removeIfOwned() {
            try { requireOwnership(); soap(http, gateway, "DeletePortMapping", key()); } catch (IOException ignored) { }
        }
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            valid = false;
            if (renewer != null) renewer.interrupt();
            removeIfOwned(); http.close();
        }
    }
}
