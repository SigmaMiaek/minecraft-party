package pl.minecraftparty.core;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;

/** TLS lobby listener and authenticated stream tunnel. No Minecraft dependency. */
public final class HostNode implements AutoCloseable {
    private final LobbyModel model;
    private final UUID hostId;
    private final TlsIdentity identity;
    private final ServerSocket listener;
    private final List<String> candidates;
    private final Map<UUID, Control> controls = new HashMap<>();
    private final Set<UUID> playersInWorld = new HashSet<>();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final Semaphore slots = new Semaphore(48);
    private final Consumer<LobbyState> onState;
    private final Thread maintenance;
    private volatile boolean closed;
    private InetSocketAddress worldTarget;
    private UUID worldId;
    private boolean multiplayer;
    public record LeadershipEvent(UUID sender, LeadershipPacket packet) { }
    private volatile Consumer<LeadershipEvent> leadershipListener = event -> {};
    private LeadershipPlan leadershipPlan;
    private final Set<UUID> leadershipAcks = new HashSet<>();
    private CompletableFuture<Void> leadershipDelivered;
    private final AtomicLong tcpAccepted = new AtomicLong(), tlsAccepted = new AtomicLong(), tlsFailed = new AtomicLong(),
            lobbyAccepted = new AtomicLong(), lobbyRejected = new AtomicLong(), protocolRejected = new AtomicLong();
    public record ConnectionStats(int localPort, long tcpAccepted, long tlsAccepted, long tlsFailed,
                                  long lobbyAccepted, long lobbyRejected, long protocolRejected) { }
    public ConnectionStats connectionStats() {
        return new ConnectionStats(listener.getLocalPort(), tcpAccepted.get(), tlsAccepted.get(), tlsFailed.get(),
                lobbyAccepted.get(), lobbyRejected.get(), protocolRejected.get());
    }

    public HostNode(UUID hostId, String hostName, String name, int limit, Consumer<LobbyState> onState)
            throws GeneralSecurityException, IOException {
        this(hostId, hostName, name, limit, DirectCandidates.gather(), InetAddress.getByName("0.0.0.0"), onState);
    }
    /** Explicit bind and advertised candidates also allow isolated integration tests. */
    public HostNode(UUID hostId, String hostName, String name, int limit, List<String> candidates,
                    InetAddress bind, Consumer<LobbyState> onState) throws GeneralSecurityException, IOException {
        this(new LobbyModel(hostId, hostName, name, limit), hostId, candidates, bind, onState);
    }
    public HostNode(UUID hostId, String hostName, String name, int limit, boolean publicLobby, List<String> candidates,
                    InetAddress bind, Consumer<LobbyState> onState) throws GeneralSecurityException, IOException {
        this(new LobbyModel(hostId, hostName, name, limit, publicLobby), hostId, candidates, bind, onState);
    }
    public static HostNode successor(LeadershipPlan plan, List<String> candidates, InetAddress bind, Consumer<LobbyState> onState)
            throws GeneralSecurityException, IOException {
        return new HostNode(LobbyModel.restore(plan), plan.successor(), candidates, bind, onState);
    }
    private HostNode(LobbyModel model, UUID hostId, List<String> candidates, InetAddress bind, Consumer<LobbyState> onState)
            throws GeneralSecurityException, IOException {
        if (candidates.isEmpty()) throw new IOException("no network interface");
        this.hostId = hostId; this.model = model;
        this.identity = TlsIdentity.create(); this.candidates = List.copyOf(candidates); this.onState = onState;
        // Validate everything before opening the socket.
        new Invite(model.snapshot().partyId(), candidates, 1, identity.fingerprint(), model.inviteSecret(), model.inviteExpires());
        listener = new ServerSocket(0, 32, bind);
        Thread.ofVirtual().name("party-host-accept").start(this::accept);
        maintenance = Thread.ofVirtual().name("party-host-maintenance").start(() -> {
            try {
                while (!closed) { Thread.sleep(3000); synchronized (this) { if (!closed) publish(); } }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        publish();
    }
    public LobbyState snapshot() { return model.snapshot(); }
    public void leadershipListener(Consumer<LeadershipEvent> listener) { leadershipListener = Objects.requireNonNull(listener); }
    public synchronized LeadershipPlan planLeadership(UUID target, boolean leaving) {
        if (closed || leadershipPlan != null || !controls.containsKey(target) || worldId != null)
            throw new IllegalStateException("leadership unavailable");
        Map<UUID, String> credentials = new HashMap<>(model.migrationCredentials()); credentials.remove(target);
        if (!leaving) credentials.put(hostId, Secrets.token());
        return new LeadershipPlan(UUID.randomUUID(), target, leaving, model.snapshot(), credentials, model.migrationBans());
    }
    public synchronized void beginLeadership(LeadershipPlan plan) throws IOException {
        if (closed || leadershipPlan != null || !hostId.equals(plan.previousLeader())
                || !model.snapshot().partyId().equals(plan.snapshot().partyId()) || !controls.containsKey(plan.successor()) || worldId != null)
            throw new IOException("leadership unavailable");
        leadershipPlan = plan;
        for (Control control : controls.values()) control.send(control.id.equals(plan.successor())
                ? new LeadershipPacket.Prepare(plan) : new LeadershipPacket.Starting(plan.transaction()));
    }
    public synchronized void abortLeadership(UUID transaction) {
        if (leadershipPlan == null || !leadershipPlan.transaction().equals(transaction)) return;
        for (Control control : controls.values()) control.send(new LeadershipPacket.Abort(transaction));
        leadershipPlan = null; leadershipAcks.clear();
        if (leadershipDelivered != null) leadershipDelivered.completeExceptionally(new IOException("aborted"));
        leadershipDelivered = null;
    }
    public synchronized CompletableFuture<Void> commitLeadership(UUID transaction, Invite invitation) throws IOException {
        if (leadershipPlan == null || !leadershipPlan.transaction().equals(transaction)
                || !controls.containsKey(leadershipPlan.successor()) || !model.snapshot().partyId().equals(invitation.partyId())) throw new IOException("leadership transaction");
        leadershipAcks.clear(); leadershipAcks.addAll(controls.keySet()); leadershipDelivered = new CompletableFuture<>();
        for (Control control : controls.values()) control.send(new LeadershipPacket.Commit(transaction, leadershipPlan.successor(), invitation));
        if (leadershipAcks.isEmpty()) leadershipDelivered.complete(null);
        return leadershipDelivered;
    }
    public Invite invite() {
        return new Invite(model.snapshot().partyId(), candidates, listener.getLocalPort(), identity.fingerprint(), model.inviteSecret(), model.inviteExpires());
    }
    public synchronized void rotateInvite() { model.rotateInvite(); publish(); }
    public synchronized void kick(UUID id) {
        Control c = controls.remove(id);
        model.kick(id);
        if (c != null) c.close();
        publish();
    }
    public synchronized void openWorld(InetSocketAddress target, UUID id) {
        if (!target.getAddress().isLoopbackAddress()) throw new IllegalArgumentException("world must bind to loopback");
        closeWorld(); worldTarget = target; worldId = Objects.requireNonNull(id); model.world(id); publish();
    }
    public synchronized void openMultiplayer(MultiplayerTarget target, UUID session) {
        closeWorld(); worldId = Objects.requireNonNull(session); multiplayer = true;
        model.multiplayer(session, target); publish();
    }
    public synchronized boolean chat(String text) {
        if (leadershipPlan != null) return false;
        boolean accepted = model.chat(hostId, text); if (accepted) publish(); return accepted;
    }
    public synchronized void closeWorld() {
        worldTarget = null; worldId = null; multiplayer = false;
        playersInWorld.clear();
        controls.values().forEach(Control::closeTunnel);
        model.world(null); publish();
    }
    public synchronized void closeWorld(UUID expectedSession) {
        if (Objects.equals(worldId, expectedSession)) closeWorld();
    }
    /** Called only after a vanilla PLAY login or disconnect event on the host. */
    public synchronized void playerInWorld(UUID id, boolean present) {
        if (present && worldId != null) playersInWorld.add(id); else playersInWorld.remove(id);
        if (controls.containsKey(id)) model.status(id, present && worldId != null ? LobbyState.Status.IN_WORLD : LobbyState.Status.LOBBY);
        publish();
    }
    private void accept() {
        while (!closed) {
            try {
                Socket transport = listener.accept();
                tcpAccepted.incrementAndGet();
                if (!slots.tryAcquire()) { transport.close(); continue; }
                SSLSocket socket;
                try {
                    socket = (SSLSocket) identity.context().getSocketFactory().createSocket(transport,
                            transport.getInetAddress().getHostAddress(), transport.getPort(), true);
                    socket.setUseClientMode(false);
                    socket.setEnabledProtocols(new String[]{"TLSv1.3"});
                    SocketPipe.track(socket, transport);
                } catch (IOException | RuntimeException e) { SocketPipe.close(transport); slots.release(); continue; }
                sockets.add(socket);
                Thread.ofVirtual().name("party-host-peer").start(() -> {
                    try { handle(socket); }
                    catch (IOException | GeneralSecurityException | IllegalArgumentException | SecurityException ignored) { /* Untrusted peer; no secret-bearing log. */ }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { SocketPipe.close(socket); sockets.remove(socket); slots.release(); }
                });
            } catch (IOException e) { if (!closed) close(); }
        }
    }
    private void handle(SSLSocket socket) throws IOException, GeneralSecurityException, InterruptedException {
        socket.setSoTimeout(10000); socket.setTcpNoDelay(true);
        try { socket.startHandshake(); tlsAccepted.incrementAndGet(); }
        catch (IOException e) { tlsFailed.incrementAndGet(); throw e; }
        var in = new DataInputStream(socket.getInputStream()); var out = new DataOutputStream(socket.getOutputStream());
        if (in.readInt() != Wire.MAGIC) { protocolRejected.incrementAndGet(); return; }
        int mode = in.readUnsignedByte(); UUID party = Wire.uuid(in); UUID id = Wire.uuid(in);
        if (!party.equals(model.snapshot().partyId())) { protocolRejected.incrementAndGet(); return; }
        if (mode == Wire.CONTROL) control(socket, in, out, id);
        else if (mode == Wire.TUNNEL) tunnel(socket, in, out, id);
    }
    private void control(SSLSocket socket, DataInputStream in, DataOutputStream out, UUID id) throws IOException {
        String name = Wire.text(in, 16), secret = Wire.text(in, 43), resume = Wire.text(in, 43);
        Control control;
        synchronized (this) {
            if (closed || controls.containsKey(id) || leadershipPlan != null && !model.authenticate(id, resume)) { lobbyRejected.incrementAndGet(); out.writeByte(Wire.ERROR); out.writeUTF("rejected"); out.flush(); return; }
            String credential;
            try { credential = model.join(id, name, secret, resume); }
            catch (SecurityException | IllegalArgumentException e) { lobbyRejected.incrementAndGet(); out.writeByte(Wire.ERROR); out.writeUTF("rejected"); out.flush(); return; }
            control = new Control(id, name, socket, out);
            control.credential = credential;
            controls.put(id, control);
        }
        try {
            out.writeByte(Wire.WELCOME); out.writeUTF(control.credential); out.flush();
            lobbyAccepted.incrementAndGet();
            control.credential = null;
            Thread.ofVirtual().name("party-host-updates").start(control::writeUpdates);
            synchronized (this) { publish(); }
            long window = System.nanoTime(); int commands = 0;
            while (!closed) {
                int command = in.readUnsignedByte();
                long now = System.nanoTime();
                if (now - window > 1_000_000_000L) { window = now; commands = 0; }
                if (++commands > 20) throw new IOException("rate");
                if (command == Wire.LEAVE) { synchronized (this) { model.leave(id); } break; }
                else if (command == Wire.STATUS) {
                    var status = Wire.status(in);
                    if (status == LobbyState.Status.DISCONNECTED) throw new IOException("status");
                    synchronized (this) {
                        model.status(id, worldId == null ? LobbyState.Status.LOBBY : multiplayer ? status : playersInWorld.contains(id)
                                ? LobbyState.Status.IN_WORLD : status == LobbyState.Status.LOBBY
                                ? LobbyState.Status.LOBBY : LobbyState.Status.CONNECTING);
                        publish();
                    }
                } else if (command == Wire.CHAT) {
                    String text = Wire.text(in, LobbyModel.MAX_CHAT_LENGTH);
                    synchronized (this) { if (leadershipPlan == null && model.chat(id, text)) publish(); }
                } else if (command >= 20 && command <= 26) {
                    if (command != 21 && command != 24 && command != 25) throw new IOException("leadership direction");
                    LeadershipPacket packet = LeadershipPacket.read(command, in);
                    synchronized (this) {
                        if (leadershipPlan == null || !leadershipPlan.transaction().equals(packet.transaction())) {
                            if (packet instanceof LeadershipPacket.Ready || packet instanceof LeadershipPacket.Failed || packet instanceof LeadershipPacket.Ack) continue;
                            throw new IOException("leadership transaction");
                        }
                        if (!(packet instanceof LeadershipPacket.Ack) && !id.equals(leadershipPlan.successor()))
                            throw new IOException("leadership authority");
                        if (packet instanceof LeadershipPacket.Ack) {
                            leadershipAcks.remove(id);
                            if (leadershipDelivered != null && leadershipAcks.isEmpty()) leadershipDelivered.complete(null);
                        } else if (packet instanceof LeadershipPacket.Ready ready) {
                            if (!model.snapshot().partyId().equals(ready.invite().partyId())) throw new IOException("leadership party");
                        } else if (!(packet instanceof LeadershipPacket.Failed)) throw new IOException("leadership direction");
                    }
                    if (!(packet instanceof LeadershipPacket.Ack)) leadershipListener.accept(new LeadershipEvent(id, packet));
                } else if (command != Wire.PING) throw new IOException("command");
                // A periodic snapshot is also a server heartbeat, independent of state revisions.
                control.offer(model.snapshot());
            }
        } finally {
            synchronized (this) {
                if (controls.remove(id, control)) { model.disconnected(id); control.close(); publish(); }
            }
        }
    }
    private void tunnel(SSLSocket socket, DataInputStream in, DataOutputStream out, UUID id)
            throws IOException, InterruptedException {
        String resume = Wire.text(in, 43); UUID requestedWorld = Wire.uuid(in);
        Control control; InetSocketAddress target;
        synchronized (this) {
            control = controls.get(id);
            if (closed || control == null || !model.authenticate(id, resume) || !requestedWorld.equals(worldId)
                    || control.tunnel != null || worldTarget == null) { out.writeByte(Wire.ERROR); out.flush(); return; }
            target = worldTarget; control.tunnel = socket;
        }
        try (Socket backend = new Socket()) {
            synchronized (this) {
                if (closed || !requestedWorld.equals(worldId) || controls.get(id) != control) return;
                control.backend = backend;
            }
            backend.connect(target, 5000); backend.setTcpNoDelay(true);
            out.writeByte(Wire.WELCOME); out.flush();
            MinecraftAdmission.forward(in, backend.getOutputStream(), id, control.name);
            socket.setSoTimeout(0);
            SocketPipe.transfer(socket, backend);
        } finally {
            synchronized (this) { if (control.tunnel == socket) control.closeTunnel(); }
        }
    }
    private synchronized void publish() {
        LobbyState snapshot = model.snapshot();
        controls.values().forEach(c -> c.offer(snapshot));
        onState.accept(snapshot);
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; SocketPipe.close(listener);
        if (maintenance != null) maintenance.interrupt();
        controls.values().forEach(Control::close); controls.clear();
        sockets.forEach(SocketPipe::close); sockets.clear();
    }
    private static final class Control implements AutoCloseable {
        final UUID id;
        final String name;
        final SSLSocket socket;
        final DataOutputStream out;
        record Outbound(LobbyState snapshot, LeadershipPacket signal) { }
        final ArrayBlockingQueue<Outbound> updates = new ArrayBlockingQueue<>(16);
        volatile boolean closed;
        Socket tunnel, backend;
        Thread writer;
        String credential;
        Control(UUID id, String name, SSLSocket socket, DataOutputStream out) { this.id = id; this.name = name; this.socket = socket; this.out = out; }
        void offer(LobbyState state) { updates.removeIf(m -> m.snapshot() != null); updates.offer(new Outbound(state, null)); }
        void send(LeadershipPacket signal) { if (!updates.offer(new Outbound(null, signal))) Thread.ofVirtual().start(this::close); }
        void writeUpdates() {
            writer = Thread.currentThread();
            try {
                while (!closed) {
                    var message = updates.take();
                    if (message.snapshot() != null) { out.writeByte(Wire.SNAPSHOT); Wire.snapshot(out, message.snapshot()); }
                    else LeadershipPacket.write(out, message.signal());
                    out.flush();
                }
            } catch (IOException e) { SocketPipe.close(socket); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        void closeTunnel() { SocketPipe.close(tunnel); SocketPipe.close(backend); tunnel = null; backend = null; }
        public void close() { closed = true; SocketPipe.close(socket); closeTunnel(); if (writer != null) writer.interrupt(); }
    }
}
