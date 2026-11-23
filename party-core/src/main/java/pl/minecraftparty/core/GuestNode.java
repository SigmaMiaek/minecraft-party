package pl.minecraftparty.core;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.GeneralSecurityException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Reconnect credentials stay in memory; the UI never receives them. */
public final class GuestNode implements AutoCloseable {
    private final Invite invite;
    private final UUID id;
    private final String name;
    private final SSLContext tls;
    private final Consumer<LobbyState> onState;
    private final Consumer<String> onConnection;
    private final AtomicReference<Socket> pending = new AtomicReference<>();
    private volatile boolean closed, connected;
    private volatile SSLSocket control;
    private volatile DataOutputStream controlOut;
    private volatile String resume = "";
    private volatile LobbyState.Status status = LobbyState.Status.LOBBY;
    private volatile LobbyState state;
    private volatile WatchState watch;
    public WatchState watch() { return watch; }
    private LocalProxy proxy;
    private final Thread worker;
    private final ConnectionTrace trace = new ConnectionTrace();
    private volatile int connectedCandidate;
    private final Consumer<LeadershipPacket> leadershipListener;

    public GuestNode(Invite invite, UUID id, String name, Consumer<LobbyState> onState, Consumer<String> onConnection)
            throws GeneralSecurityException {
        this(invite, id, name, "", onState, onConnection, packet -> {});
    }
    public GuestNode(Invite invite, UUID id, String name, String resume, Consumer<LobbyState> onState,
                     Consumer<String> onConnection, Consumer<LeadershipPacket> leadershipListener) throws GeneralSecurityException {
        this.invite = invite; this.id = id; this.name = LobbyModel.validName(name, 16);
        if (!resume.isEmpty() && !resume.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("credential");
        this.resume = resume; this.leadershipListener = leadershipListener;
        this.onState = onState; this.onConnection = onConnection; this.tls = TlsIdentity.client(invite.fingerprint());
        worker = Thread.ofVirtual().name("party-guest-control").start(this::run);
    }
    public boolean connected() { return connected; }
    public boolean relay() { return invite.relay(); }
    public String diagnostics() { return trace.report(); }
    public LobbyState snapshot() { return state; }
    public void status(LobbyState.Status value) { status = value; }
    public String migrationCredential() { return resume; }
    public void leadership(LeadershipPacket packet) throws IOException {
        if (!(packet instanceof LeadershipPacket.Ready || packet instanceof LeadershipPacket.Failed)) throw new IOException("direction");
        DataOutputStream out = controlOut;
        if (!connected || closed || out == null) throw new IOException("not connected");
        synchronized (out) { LeadershipPacket.write(out, packet); out.flush(); }
    }
    /** Worker-thread operation; serialized with heartbeats and graceful leave. */
    public void chat(String text) throws IOException {
        String clean = LobbyModel.validName(text, LobbyModel.MAX_CHAT_LENGTH).strip();
        DataOutputStream out = controlOut;
        if (!connected || closed || out == null) throw new IOException("not connected");
        synchronized (out) { out.writeByte(Wire.CHAT); out.writeUTF(clean); out.flush(); }
    }
    private void run() {
        long lostAt = 0;
        while (!closed) {
            try (var connection = connect()) {
                SSLSocket socket = connection.socket();
                control = socket;
                var out = new DataOutputStream(socket.getOutputStream());
                var in = new DataInputStream(socket.getInputStream());
                header(out, Wire.CONTROL); out.writeUTF(name); out.writeUTF(invite.secret()); out.writeUTF(resume); out.flush();
                if (in.readUnsignedByte() != Wire.WELCOME) throw new SecurityException("rejected");
                resume = Wire.text(in, 43);
                if (!resume.matches("[A-Za-z0-9_-]{43}")) throw new IOException("credential");
                controlOut = out; connected = true; lostAt = 0; onConnection.accept("connected");
                trace.mark(connectedCandidate, NetworkFailure.Phase.LOBBY, ConnectionTrace.Event.AUTHENTICATED);
                Thread heartbeat = Thread.ofVirtual().name("party-guest-heartbeat").start(() -> heartbeat(socket, out));
                try {
                    while (!closed) {
                        int kind = in.readUnsignedByte();
                        if (kind == Wire.WATCH) {
                            WatchState next = WatchWire.read(in);
                            if (next != null && (state == null || !next.worldId().equals(state.worldId())
                                    || watch != null && watch.sessionId().equals(next.sessionId()) && next.revision() < watch.revision()))
                                throw new IOException("watch session");
                            watch = next; continue;
                        }
                        if (kind >= 20 && kind <= 26) {
                            if (kind != 20 && kind != 22 && kind != 23 && kind != 26) throw new IOException("leadership direction");
                            LeadershipPacket packet = LeadershipPacket.read(kind, in);
                            if (!(packet instanceof LeadershipPacket.Prepare || packet instanceof LeadershipPacket.Commit
                                    || packet instanceof LeadershipPacket.Abort || packet instanceof LeadershipPacket.Starting)) throw new IOException("direction");
                            if (packet instanceof LeadershipPacket.Prepare prepare && (!prepare.plan().successor().equals(id)
                                    || !prepare.plan().snapshot().partyId().equals(invite.partyId()))) throw new IOException("successor");
                            if (packet instanceof LeadershipPacket.Commit commit) {
                                if (!commit.invite().partyId().equals(invite.partyId())) throw new IOException("migration party");
                                synchronized (out) { LeadershipPacket.write(out, new LeadershipPacket.Ack(packet.transaction())); out.flush(); }
                            }
                            leadershipListener.accept(packet); continue;
                        }
                        if (kind != Wire.SNAPSHOT) throw new IOException("message");
                        LobbyState next = Wire.snapshot(in);
                        if (!invite.partyId().equals(next.partyId()) || next.members().stream().noneMatch(m -> m.id().equals(id))
                                || state != null && next.revision() < state.revision()) throw new IOException("state");
                        state = next; onState.accept(next);
                    }
                } finally { heartbeat.interrupt(); }
            } catch (SecurityException e) {
                trace.mark(connectedCandidate, NetworkFailure.Phase.LOBBY, ConnectionTrace.Event.REJECTED);
                if (!closed) onConnection.accept("rejected"); break;
            }
            catch (IOException e) {
                if (closed) break;
                NetworkFailure failure = NetworkFailure.from(NetworkFailure.Phase.LOBBY, e);
                if (!(e instanceof NetworkFailure)) trace.failure(connectedCandidate, failure);
                if (resume.isEmpty()) { onConnection.accept(failure.key(invite.relay())); break; }
                if (lostAt == 0) lostAt = System.nanoTime();
                onConnection.accept("reconnecting");
                if (System.nanoTime() - lostAt > 110_000_000_000L) { onConnection.accept(failure.key(invite.relay())); break; }
                trace.mark(0, failure.phase(), ConnectionTrace.Event.RETRYING);
                try { Thread.sleep(3000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            } finally {
                connected = false; control = null; controlOut = null;
                stopProxy();
            }
        }
    }
    private void heartbeat(SSLSocket socket, DataOutputStream out) {
        try {
            while (!closed && !socket.isClosed()) {
                synchronized (out) { out.writeByte(Wire.STATUS); out.writeByte(status.ordinal()); out.flush(); }
                Thread.sleep(3000);
            }
        } catch (IOException e) { SocketPipe.close(socket); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    private SocketPipe.TlsConnection connect() throws IOException {
        NetworkFailure best = null; int candidateNumber = 0;
        for (String candidate : invite.relay() ? java.util.List.of(invite.relayDomain()) : invite.candidates()) {
            if (closed) throw new IOException("closed");
            candidateNumber++;
            SSLSocket socket = null;
            Socket raw = null;
            NetworkFailure.Phase phase = NetworkFailure.Phase.TCP;
            try {
                trace.mark(candidateNumber, phase, ConnectionTrace.Event.CONNECTING);
                raw = new Socket(); pending.set(raw);
                if (closed) throw new IOException("closed");
                raw.setSoTimeout(10000); raw.setTcpNoDelay(true);
                raw.connect(new InetSocketAddress(InetAddress.getByName(candidate), invite.port()), 5000);
                trace.mark(candidateNumber, phase, ConnectionTrace.Event.CONNECTED);
                phase = NetworkFailure.Phase.TLS;
                if (invite.relay()) { raw.getOutputStream().write(E4mcProtocol.routingPreface(candidate)); raw.getOutputStream().flush(); }
                socket = (SSLSocket) tls.getSocketFactory().createSocket(raw, candidate, invite.port(), true);
                SocketPipe.track(socket, raw);
                pending.compareAndSet(raw, socket);
                if (closed) throw new IOException("closed");
                socket.setEnabledProtocols(new String[]{"TLSv1.3"}); socket.setSoTimeout(10000);
                socket.startHandshake();
                trace.mark(candidateNumber, phase, ConnectionTrace.Event.CONNECTED);
                connectedCandidate = candidateNumber;
                return new SocketPipe.TlsConnection(socket);
            } catch (IOException e) {
                SocketPipe.close(socket);
                SocketPipe.close(raw);
                NetworkFailure failure = NetworkFailure.from(phase, e); trace.failure(candidateNumber, failure);
                best = NetworkFailure.prefer(best, failure);
            }
            finally { pending.compareAndSet(socket, null); pending.compareAndSet(raw, null); }
        }
        throw best == null ? new IOException("unreachable") : best;
    }
    private void header(DataOutputStream out, int mode) throws IOException {
        out.writeInt(Wire.MAGIC); out.writeByte(mode); Wire.uuid(out, invite.partyId()); Wire.uuid(out, id);
    }
    /** Must be invoked on a worker, never on the render thread. */
    public synchronized int prepareWorld(UUID world) throws IOException {
        stopProxy();
        if (!connected || state == null || !world.equals(state.worldId()) || state.multiplayerTarget() != null)
            throw new IOException("world unavailable");
        proxy = new LocalProxy(world); return proxy.port();
    }
    public synchronized void stopProxy() { SocketPipe.close(proxy); proxy = null; }
    @Override public void close() {
        closed = true;
        // Closing sockets is non-blocking; graceful leave is best effort on a separate thread.
        SSLSocket socket = control; DataOutputStream out = controlOut;
        if (socket != null && out != null) Thread.ofVirtual().start(() -> {
            try { synchronized (out) { out.writeByte(Wire.LEAVE); out.flush(); } }
            catch (IOException ignored) { }
            finally { SocketPipe.close(socket); worker.interrupt(); }
        });
        else { SocketPipe.close(socket); worker.interrupt(); }
        SocketPipe.close(pending.getAndSet(null)); stopProxy();
    }
    private final class LocalProxy implements AutoCloseable {
        private final ServerSocket local;
        private volatile Socket minecraft;
        private volatile SSLSocket remote;
        private volatile boolean stopped;
        LocalProxy(UUID world) throws IOException {
            local = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            local.setSoTimeout(20000);
            Thread.ofVirtual().name("party-world-proxy").start(() -> run(world));
        }
        int port() { return local.getLocalPort(); }
        private void run(UUID world) {
            try (Socket client = local.accept()) {
                minecraft = client; SocketPipe.close(local);
                if (stopped) return;
                client.setTcpNoDelay(true);
                try (var connection = connect()) {
                    SSLSocket socket = connection.socket();
                    remote = socket;
                    if (stopped) return;
                    var out = new DataOutputStream(socket.getOutputStream()); var in = new DataInputStream(socket.getInputStream());
                    header(out, Wire.TUNNEL); out.writeUTF(resume); Wire.uuid(out, world); out.flush();
                    if (in.readUnsignedByte() != Wire.WELCOME) throw new IOException("tunnel rejected");
                    socket.setSoTimeout(0);
                    SocketPipe.transfer(client, socket);
                }
            } catch (IOException e) {
                if (!closed && !stopped) onConnection.accept(e instanceof NetworkFailure failure ? failure.key(invite.relay()) : "tunnel_failed");
            }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { close(); }
        }
        public void close() { stopped = true; SocketPipe.close(local); SocketPipe.close(minecraft); SocketPipe.close(remote); }
    }
}
