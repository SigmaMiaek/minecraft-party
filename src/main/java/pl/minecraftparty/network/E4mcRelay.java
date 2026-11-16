package pl.minecraftparty.network;

import com.google.gson.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.*;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.incubator.codec.quic.*;
import io.netty.util.ReferenceCountUtil;
import pl.minecraftparty.core.*;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** One outbound QUIC session carries the private TLS listener, including while Minecraft has no world. */
public final class E4mcRelay implements AutoCloseable {
    public static final class Failure extends IOException {
        private final String key;
        Failure(String key) { super(key); this.key = key; }
        public String key() { return key; }
    }
    private record Relay(String host, int port) { }
    private final NioEventLoopGroup group = new NioEventLoopGroup(1);
    private final Set<Channel> channels = ConcurrentHashMap.newKeySet();
    private final Semaphore slots = new Semaphore(32);
    private final AtomicBoolean closed = new AtomicBoolean(), healthy = new AtomicBoolean();
    private final AtomicLong incomingStreams = new AtomicLong(), routingAccepted = new AtomicLong(), routingRejected = new AtomicLong(),
            routingReadResumed = new AtomicLong(), backendConnected = new AtomicLong(), backendFailed = new AtomicLong(),
            toTlsBytes = new AtomicLong(), fromTlsBytes = new AtomicLong();
    private final CompletableFuture<String> assigned = new CompletableFuture<>();
    private final int localPort;
    private final Consumer<String> lost;
    private final UUID partyId;
    private final String fingerprint;
    private volatile String domain;
    private Channel datagram;
    private QuicSslContext ssl;

    private E4mcRelay(Invite local, Consumer<String> lost) {
        localPort = local.port();
        partyId = local.partyId(); fingerprint = local.fingerprint(); this.lost = lost;
    }
    public static E4mcRelay open(Invite local, BooleanSupplier cancelled, Consumer<String> lost) throws IOException {
        E4mcRelay relay = null;
        try {
            check(cancelled);
            try { RelayNativeLibrary.prepare(); } catch (IOException e) { throw new Failure("relay_native"); }
            Quic.ensureAvailability(); // Loads the verified native prepared above; runs only on the Party worker.
            check(cancelled);
            Relay destination = broker();
            relay = new E4mcRelay(local, lost);
            relay.start(destination);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
            while (true) {
                check(cancelled);
                if (System.nanoTime() >= deadline) throw new Failure("relay_timeout");
                try { relay.domain = relay.assigned.get(150, TimeUnit.MILLISECONDS); break; }
                catch (TimeoutException ignored) { }
            }
            check(cancelled); return relay;
        } catch (LinkageError e) {
            if (relay != null) relay.close(); throw new Failure("relay_native");
        } catch (InterruptedException e) {
            if (relay != null) relay.close(); Thread.currentThread().interrupt(); throw new Failure("relay_unavailable");
        } catch (ExecutionException | RuntimeException e) {
            if (relay != null) relay.close(); throw new Failure("relay_unavailable");
        } catch (IOException e) {
            if (relay != null) relay.close(); throw e;
        }
    }
    private static void check(BooleanSupplier cancelled) throws Failure {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Failure("relay_unavailable");
    }
    private static Relay broker() throws Failure {
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            var request = HttpRequest.newBuilder(URI.create("https://broker.e4mc.link/getBestRelay"))
                    .timeout(Duration.ofSeconds(7)).header("Accept", "application/json").GET().build();
            var response = http.send(request, info -> new LimitedHttpBody(E4mcProtocol.MAX_CONTROL));
            if (response.statusCode() != 200) throw new Failure("relay_broker");
            JsonObject json = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            String host = E4mcProtocol.domain(json.get("host").getAsString());
            int port = json.get("port").getAsInt();
            if (port < 1024 || port > 65535) throw new Failure("relay_broker");
            return new Relay(host, port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new Failure("relay_broker");
        } catch (IOException | RuntimeException e) { throw new Failure("relay_broker"); }
    }
    private void start(Relay relay) throws IOException {
        ssl = QuicSslContextBuilder.forClient().applicationProtocols("quiclime").build();
        ChannelHandler codec = new QuicClientCodecBuilder().sslContext(ssl)
                .sslEngineProvider(channel -> ssl.newEngine(channel.alloc(), relay.host(), relay.port()))
                .maxIdleTimeout(30, TimeUnit.SECONDS).initialMaxStreamsBidirectional(32)
                .initialMaxData(8 * 1024 * 1024).initialMaxStreamDataBidirectionalLocal(256 * 1024)
                .initialMaxStreamDataBidirectionalRemote(256 * 1024).build();
        new Bootstrap().group(group).channel(NioDatagramChannel.class).handler(codec).bind(0).addListener(bound -> {
            if (!bound.isSuccess()) { fail(); return; }
            if (closed.get()) { ((ChannelFuture) bound).channel().close(); return; }
            datagram = ((ChannelFuture) bound).channel(); channels.add(datagram);
            QuicChannel.newBootstrap(datagram).streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                protected void initChannel(QuicStreamChannel stream) {
                    incomingStreams.incrementAndGet();
                    if (closed.get() || !healthy.get() || !slots.tryAcquire()) { stream.close(); return; }
                    channels.add(stream);
                    stream.closeFuture().addListener(ignored -> { channels.remove(stream); slots.release(); });
                    stream.config().setAutoRead(false);
                    stream.pipeline().addLast("preface-timeout", new ReadTimeoutHandler(10));
                    stream.pipeline().addLast("preface", new RoutingGate(() -> domain, routingAccepted::incrementAndGet,
                            routingRejected::incrementAndGet, routingReadResumed::incrementAndGet));
                    stream.pipeline().addLast("bridge", new StreamBridge());
                }
            }).handler(new ChannelInboundHandlerAdapter() {
                public void channelInactive(ChannelHandlerContext ctx) { fail(); }
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable error) { fail(); ctx.close(); }
            }).remoteAddress(new InetSocketAddress(relay.host(), relay.port())).connect().addListener(connected -> {
                if (!connected.isSuccess()) { fail(); return; }
                if (closed.get()) { ((QuicChannel) connected.getNow()).close(); return; }
                QuicChannel channel = (QuicChannel) connected.getNow(); channels.add(channel);
                channel.closeFuture().addListener(ignored -> { channels.remove(channel); fail(); });
                channel.createStream(QuicStreamType.BIDIRECTIONAL, new Control()).addListener(created -> {
                    if (!created.isSuccess()) { fail(); return; }
                    if (closed.get()) { ((QuicStreamChannel) created.getNow()).close(); return; }
                    QuicStreamChannel control = (QuicStreamChannel) created.getNow(); channels.add(control);
                    control.writeAndFlush(controlMessage("request_domain_assignment")).addListener(write -> { if (!write.isSuccess()) fail(); });
                    var heartbeat = control.eventLoop().scheduleAtFixedRate(() -> {
                        if (!closed.get() && control.isActive()) control.writeAndFlush(controlMessage("probe_capabilities"))
                                .addListener(write -> { if (!write.isSuccess()) fail(); });
                    }, 3, 3, TimeUnit.SECONDS);
                    control.closeFuture().addListener(ignored -> { heartbeat.cancel(false); channels.remove(control); fail(); });
                });
            });
        });
    }
    private static ByteBuf controlMessage(String kind) {
        byte[] json = ("{\"kind\":\"" + kind + "\"}").getBytes(StandardCharsets.US_ASCII);
        ByteBuf packet = Unpooled.buffer(json.length + 3); writeLength(packet, json.length); return packet.writeBytes(json);
    }
    private static void writeLength(ByteBuf out, int value) {
        do { int next = value & 127; value >>>= 7; out.writeByte(value == 0 ? next : next | 128); } while (value != 0);
    }
    /** Returns -1 without consuming an incomplete prefix. Rejects oversized/noncanonical lengths early. */
    private static int length(ByteBuf in, int maximum) throws IOException {
        in.markReaderIndex(); int value = 0;
        for (int i = 0; i < 3; i++) {
            if (!in.isReadable()) { in.resetReaderIndex(); return -1; }
            int next = in.readUnsignedByte(); value |= (next & 127) << (7 * i);
            if ((next & 128) == 0) {
                if (value < 1 || value > maximum || i > 0 && (next & 127) == 0) throw new IOException("relay frame size");
                if (in.readableBytes() < value) { in.resetReaderIndex(); return -1; }
                return value;
            }
        }
        throw new IOException("relay frame length");
    }
    private final class Control extends ByteToMessageDecoder {
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws IOException {
            int size = length(in, E4mcProtocol.MAX_CONTROL); if (size < 0) return;
            byte[] bytes = new byte[size]; in.readBytes(bytes);
            try {
                JsonObject object = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                if ("domain_assignment_complete".equals(object.get("kind").getAsString())) {
                    String host = E4mcProtocol.domain(object.get("domain").getAsString());
                    if (assigned.isDone()) throw new IOException("duplicate relay assignment");
                    domain = host; healthy.set(true); assigned.complete(host);
                }
                // Relay broadcasts are not trusted UI content; capability/heartbeat replies require no action.
            } catch (RuntimeException e) { throw new IOException("relay control format"); }
        }
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable error) { fail(); ctx.close(); }
    }
    static final class RoutingGate extends ByteToMessageDecoder {
        private final Supplier<String> domain;
        private final Runnable accepted, rejected, resumed;
        RoutingGate(Supplier<String> domain) { this(domain, () -> {}, () -> {}, () -> {}); }
        RoutingGate(Supplier<String> domain, Runnable accepted, Runnable rejected, Runnable resumed) {
            this.domain = domain; this.accepted = accepted; this.rejected = rejected; this.resumed = resumed;
        }
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws IOException {
            int size = length(in, E4mcProtocol.MAX_ROUTING_PACKET); if (size < 0) return;
            byte[] bytes = new byte[size]; in.readBytes(bytes); E4mcProtocol.validateRoutingPacket(bytes, domain.get());
            boolean needsNextRead = !in.isReadable();
            accepted.run();
            ctx.pipeline().remove("preface-timeout");
            ctx.pipeline().remove(this); // ByteToMessageDecoder forwards remaining TLS bytes unchanged on removal.
            // With AUTO_READ=false, removing this decoder after a preface-only read otherwise loses
            // its automatic request for more bytes. No downstream write occurs to rearm the bridge.
            if (needsNextRead && ctx.channel().isActive()) { resumed.run(); ctx.channel().read(); }
        }
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable error) { rejected.run(); ctx.close(); }
    }
    private final class StreamBridge extends ChannelInboundHandlerAdapter {
        private Channel peer;
        public void channelActive(ChannelHandlerContext ctx) {
            new Bootstrap().group(group).channel(NioSocketChannel.class).option(ChannelOption.AUTO_READ, false)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000).option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInboundHandlerAdapter() {
                        public void channelRead(ChannelHandlerContext local, Object message) {
                            if (message instanceof ByteBuf buffer) fromTlsBytes.addAndGet(buffer.readableBytes());
                            ctx.writeAndFlush(message).addListener(sent -> { if (sent.isSuccess()) local.read(); else { local.close(); ctx.close(); } });
                        }
                        public void channelInactive(ChannelHandlerContext local) { ctx.close(); }
                        public void exceptionCaught(ChannelHandlerContext local, Throwable error) { local.close(); ctx.close(); }
                    }).connect(new InetSocketAddress("127.0.0.1", localPort)).addListener(connected -> {
                        if (!connected.isSuccess()) { backendFailed.incrementAndGet(); ctx.close(); return; }
                        backendConnected.incrementAndGet();
                        peer = ((ChannelFuture) connected).channel(); channels.add(peer);
                        peer.closeFuture().addListener(ignored -> channels.remove(peer));
                        if (!ctx.channel().isActive() || closed.get()) { peer.close(); ctx.close(); return; }
                        ctx.read(); peer.read();
                    });
        }
        public void channelRead(ChannelHandlerContext ctx, Object message) {
            if (peer == null || !peer.isActive()) { ReferenceCountUtil.release(message); ctx.close(); return; }
            if (message instanceof ByteBuf buffer) toTlsBytes.addAndGet(buffer.readableBytes());
            peer.writeAndFlush(message).addListener(sent -> { if (sent.isSuccess()) ctx.read(); else { peer.close(); ctx.close(); } });
        }
        public void channelInactive(ChannelHandlerContext ctx) { if (peer != null) peer.close(); }
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable error) { if (peer != null) peer.close(); ctx.close(); }
    }
    private void fail() {
        assigned.completeExceptionally(new Failure("relay_unavailable"));
        if (!closed.get() && healthy.getAndSet(false)) lost.accept("relay_lost");
        close();
    }
    public boolean valid() { return healthy.get() && !closed.get(); }
    public String diagnostics() {
        return "Relay incoming streams: " + incomingStreams.get() + "\nRelay routing accepted: " + routingAccepted.get()
                + "\nRelay routing rejected: " + routingRejected.get() + "\nRelay preface-only reads resumed: " + routingReadResumed.get()
                + "\nRelay backend connected: " + backendConnected.get() + "\nRelay backend connect failures: " + backendFailed.get()
                + "\nRelay -> TLS bytes: " + toTlsBytes.get() + "\nTLS -> relay bytes: " + fromTlsBytes.get() + "\n";
    }
    public Invite invitation(Invite current) {
        if (!valid()) throw new IllegalStateException("relay unavailable");
        if (!partyId.equals(current.partyId()) || !fingerprint.equals(current.fingerprint())) throw new IllegalArgumentException("relay session");
        return current.viaRelay(domain);
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        healthy.set(false); assigned.completeExceptionally(new Failure("relay_unavailable"));
        channels.forEach(Channel::close); channels.clear();
        group.shutdownGracefully(0, 2, TimeUnit.SECONDS).addListener(ignored -> ReferenceCountUtil.release(ssl));
    }
}
