package pl.minecraftparty.core;

import java.time.Clock;
import java.time.Duration;
import java.util.*;

/** Host authority. All mutations are serialized under this object's monitor. */
public final class LobbyModel {
    public static final int MAX_MEMBERS = 8; // Vanilla IntegratedServer#getMaxPlayers in Minecraft 26.2.
    public static final int MAX_CHAT_MESSAGES = 64;
    public static final int MAX_CHAT_LENGTH = 256;
    private static final long GRACE_MILLIS = Duration.ofMinutes(2).toMillis();
    private final UUID partyId;
    private final UUID hostId;
    private final String name;
    private final int limit;
    private final Clock clock;
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();
    private final Set<UUID> banned = new HashSet<>();
    private String inviteSecret;
    private long inviteExpires;
    private long revision;
    private UUID worldId;
    private MultiplayerTarget multiplayerTarget;
    private final ArrayDeque<LobbyState.ChatMessage> chat = new ArrayDeque<>();
    private final Map<UUID, Long> lastChat = new HashMap<>();
    private long chatSequence;

    public LobbyModel(UUID hostId, String hostName, String name, int limit) {
        this(hostId, hostName, name, limit, Clock.systemUTC());
    }
    LobbyModel(UUID hostId, String hostName, String name, int limit, Clock clock) {
        this(UUID.randomUUID(), hostId, hostName, name, limit, clock);
    }
    private LobbyModel(UUID partyId, UUID hostId, String hostName, String name, int limit, Clock clock) {
        this.partyId = Objects.requireNonNull(partyId);
        if (limit < 2 || limit > MAX_MEMBERS) throw new IllegalArgumentException("limit");
        this.hostId = Objects.requireNonNull(hostId);
        this.name = validName(name, 40);
        this.limit = limit;
        this.clock = clock;
        entries.put(hostId, new Entry(validName(hostName, 16), "", LobbyState.Status.LOBBY));
        rotateInvite();
    }
    public static LobbyModel restore(LeadershipPlan plan) {
        LobbyState state = plan.snapshot();
        var leader = state.members().stream().filter(m -> m.id().equals(plan.successor())).findFirst().orElseThrow();
        var model = new LobbyModel(state.partyId(), leader.id(), leader.name(), state.name(), state.limit(), Clock.systemUTC());
        model.entries.clear();
        for (var member : state.members()) {
            if (plan.leaving() && member.host()) continue;
            boolean host = member.id().equals(leader.id());
            Entry entry = new Entry(member.name(), host ? "" : plan.credentials().get(member.id()), host ? LobbyState.Status.LOBBY : LobbyState.Status.DISCONNECTED);
            if (!host) entry.disconnectedAt = model.clock.millis();
            model.entries.put(member.id(), entry);
        }
        model.banned.addAll(plan.banned()); model.chat.addAll(state.chat());
        model.chatSequence = state.chat().isEmpty() ? 0 : state.chat().getLast().sequence();
        model.revision = state.revision() + 1;
        return model;
    }
    synchronized Map<UUID, String> migrationCredentials() {
        Map<UUID, String> result = new HashMap<>(); entries.forEach((id, entry) -> { if (!id.equals(hostId)) result.put(id, entry.resume); });
        return result;
    }
    synchronized Set<UUID> migrationBans() { return Set.copyOf(banned); }
    public synchronized String join(UUID id, String playerName, String invite, String resume) {
        expireDisconnected();
        if (id.equals(hostId) || banned.contains(id)) throw new SecurityException("rejected");
        Entry existing = entries.get(id);
        if (existing != null) {
            if (!Secrets.equal(existing.resume, resume) || !existing.name.equals(playerName)) throw new SecurityException("rejected");
            existing.disconnectedAt = -1;
            existing.status = LobbyState.Status.LOBBY;
            revision++;
            return existing.resume;
        }
        if (!Secrets.equal(inviteSecret, invite) || clock.millis() >= inviteExpires)
            throw new SecurityException("invite");
        if (entries.size() >= limit) throw new SecurityException("full");
        String cleanName = validName(playerName, 16);
        if (!cleanName.matches("[A-Za-z0-9_]{1,16}") || entries.values().stream()
                .anyMatch(e -> e.name.equalsIgnoreCase(cleanName))) throw new SecurityException("name");
        Entry entry = new Entry(cleanName, Secrets.token(), LobbyState.Status.LOBBY);
        entries.put(id, entry);
        revision++;
        return entry.resume;
    }
    public synchronized boolean authenticate(UUID id, String resume) {
        Entry e = entries.get(id);
        return e != null && !id.equals(hostId) && e.disconnectedAt < 0 && Secrets.equal(e.resume, resume);
    }
    public synchronized void status(UUID id, LobbyState.Status status) {
        Entry e = entries.get(id);
        if (e != null && e.status != status) { e.status = status; revision++; }
    }
    public synchronized void disconnected(UUID id) {
        Entry e = entries.get(id);
        if (e != null && !id.equals(hostId)) {
            e.disconnectedAt = clock.millis(); e.status = LobbyState.Status.DISCONNECTED; revision++;
        }
    }
    public synchronized void leave(UUID id) {
        if (!id.equals(hostId) && entries.remove(id) != null) { lastChat.remove(id); revision++; }
    }
    public synchronized void kick(UUID id) {
        if (!id.equals(hostId)) { banned.add(id); leave(id); rotateInvite(); }
    }
    public synchronized void world(UUID id) {
        worldId = id; multiplayerTarget = null;
        entries.forEach((memberId, e) -> {
            if (e.status != LobbyState.Status.DISCONNECTED)
                e.status = id != null && memberId.equals(hostId) ? LobbyState.Status.IN_WORLD : LobbyState.Status.LOBBY;
        });
        revision++;
    }
    public synchronized void multiplayer(UUID session, MultiplayerTarget target) {
        world(Objects.requireNonNull(session)); multiplayerTarget = Objects.requireNonNull(target);
    }
    /** Sender identity comes from the authenticated control channel, never from chat payload. */
    public synchronized boolean chat(UUID sender, String text) {
        Entry entry = entries.get(sender);
        if (entry == null || entry.disconnectedAt >= 0) throw new SecurityException("not a member");
        String clean = validName(text, MAX_CHAT_LENGTH).strip();
        long now = clock.millis(); Long previous = lastChat.get(sender);
        if (previous != null && now - previous < 750) return false;
        lastChat.put(sender, now);
        chat.addLast(new LobbyState.ChatMessage(++chatSequence, sender, entry.name, clean));
        while (chat.size() > MAX_CHAT_MESSAGES) chat.removeFirst();
        revision++; return true;
    }
    public synchronized void rotateInvite() {
        inviteSecret = Secrets.token(); inviteExpires = clock.millis() + Duration.ofHours(1).toMillis(); revision++;
    }
    public synchronized LobbyState snapshot() {
        expireDisconnected();
        return new LobbyState(partyId, name, limit, revision, worldId, multiplayerTarget, entries.entrySet().stream()
                .map(e -> new LobbyState.Member(e.getKey(), e.getValue().name, e.getKey().equals(hostId), e.getValue().status)).toList(), List.copyOf(chat));
    }
    synchronized String inviteSecret() { return inviteSecret; }
    synchronized long inviteExpires() { return inviteExpires; }
    private void expireDisconnected() {
        if (entries.entrySet().removeIf(e -> !e.getKey().equals(hostId) && e.getValue().disconnectedAt >= 0
                && clock.millis() - e.getValue().disconnectedAt >= GRACE_MILLIS)) {
            lastChat.keySet().retainAll(entries.keySet()); revision++;
        }
    }
    static String validName(String text, int max) {
        if (text == null || text.isBlank() || text.length() > max || text.chars().anyMatch(c -> Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT || c == 0xA7))
            throw new IllegalArgumentException("name");
        return text;
    }
    private static final class Entry {
        final String name, resume;
        LobbyState.Status status;
        long disconnectedAt = -1;
        Entry(String name, String resume, LobbyState.Status status) { this.name = name; this.resume = resume; this.status = status; }
    }
}
