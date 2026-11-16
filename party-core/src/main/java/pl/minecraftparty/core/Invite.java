package pl.minecraftparty.core;

import java.io.*;
import java.net.*;
import java.time.Instant;
import java.util.*;

/** Out-of-band bearer capability. Treat it like a password, including when logging. */
public record Invite(UUID partyId, List<String> candidates, int port, String fingerprint,
                     String secret, long expiresAt, String relayDomain) {
    public static final int MAX_CODE_LENGTH = 4096;
    public static final String PREFIX = "mp2:";
    public static final String RELAY_PREFIX = "mp3:";
    /** Cheap UI eligibility check only; decode still validates the complete payload and expiry. No network I/O. */
    public static boolean hasSupportedPrefix(String code) {
        if (code == null) return false;
        String value = code.strip();
        return value.length() > PREFIX.length() && value.length() <= MAX_CODE_LENGTH
                && (value.startsWith(PREFIX) || value.startsWith(RELAY_PREFIX));
    }
    public Invite(UUID partyId, List<String> candidates, int port, String fingerprint, String secret, long expiresAt) {
        this(partyId, candidates, port, fingerprint, secret, expiresAt, null);
    }
    public Invite {
        Objects.requireNonNull(partyId);
        candidates = List.copyOf(candidates);
        if (candidates.size() > 8 || port < 1 || port > 65535
                || !fingerprint.matches("[0-9a-f]{64}") || !secret.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("invite");
        if (relayDomain == null) {
            if (candidates.isEmpty()) throw new IllegalArgumentException("no candidates");
        } else {
            relayDomain = E4mcProtocol.domain(relayDomain);
            if (!candidates.isEmpty() || port != E4mcProtocol.PORT) throw new IllegalArgumentException("relay invite");
        }
        candidates.forEach(Invite::validateLiteral);
    }
    public boolean relay() { return relayDomain != null; }
    public Invite viaRelay(String domain) {
        return new Invite(partyId, List.of(), E4mcProtocol.PORT, fingerprint, secret, expiresAt, domain);
    }
    public String encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(relay() ? 3 : 2); Wire.uuid(out, partyId); out.writeInt(port); out.writeLong(expiresAt);
            out.writeUTF(fingerprint); out.writeUTF(secret); out.writeInt(candidates.size());
            for (String candidate : candidates) out.writeUTF(candidate);
            if (relay()) out.writeUTF(relayDomain);
            return (relay() ? RELAY_PREFIX : PREFIX) + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static Invite decode(String code) throws IOException {
        if (!hasSupportedPrefix(code))
            throw new IOException("invite");
        code = code.strip();
        try {
            var in = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(code.substring(4))));
            int version = in.readInt();
            if (version != (code.startsWith(PREFIX) ? 2 : 3)) throw new IOException("version");
            UUID id = Wire.uuid(in); int port = in.readInt(); long expires = in.readLong();
            String fingerprint = Wire.text(in, 64), secret = Wire.text(in, 43);
            int count = in.readInt();
            if (count < (version == 3 ? 0 : 1) || count > 8) throw new IOException("candidates");
            List<String> candidates = new ArrayList<>();
            for (int i = 0; i < count; i++) candidates.add(Wire.text(in, 64));
            String domain = version == 3 ? Wire.text(in, 253) : null;
            if (in.available() != 0) throw new IOException("trailing data");
            Invite invite = new Invite(id, candidates, port, fingerprint, secret, expires, domain);
            if (expires <= Instant.now().toEpochMilli()) throw new IOException("expired");
            return invite;
        } catch (IllegalArgumentException e) { throw new IOException("invite", e); }
    }
    static void validateLiteral(String address) {
        if (address == null || address.length() > 64 || address.contains("%")) throw new IllegalArgumentException("address");
        if (address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
            for (String part : address.split("\\.")) if (Integer.parseInt(part) > 255) throw new IllegalArgumentException("address");
        } else if (!address.contains(":") || !address.matches("[0-9a-fA-F:]+")) {
            throw new IllegalArgumentException("literal address required");
        }
        try {
            InetAddress parsed = InetAddress.getByName(address); // Numeric literals only; never resolves DNS.
            if (parsed.isAnyLocalAddress() || parsed.isMulticastAddress() || parsed.isLinkLocalAddress()
                    || address.equals("255.255.255.255")) throw new IllegalArgumentException("address");
        } catch (UnknownHostException e) { throw new IllegalArgumentException("address", e); }
    }
    @Override public String toString() { return "Invite[REDACTED]"; }
}
