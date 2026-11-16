package pl.minecraftparty.core;

import pl.minecraftparty.core.upnp.*;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Public direct IPv4 candidate, either on the host interface or temporarily mapped by its router. */
public final class InternetExposure implements AutoCloseable {
    private final List<String> addresses;
    private final int port;
    private final UpnpPortMapper.Mapping mapping;
    private final UUID partyId;
    private final String fingerprint;
    private final AtomicBoolean closed = new AtomicBoolean();
    private InternetExposure(Invite original, List<String> addresses, int port, UpnpPortMapper.Mapping mapping) {
        this.partyId = original.partyId(); this.fingerprint = original.fingerprint();
        this.addresses = List.copyOf(addresses); this.port = port; this.mapping = mapping;
    }
    public static InternetExposure open(Invite local, BooleanSupplier cancelled, Consumer<String> lost) throws IOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new IOException("cancelled");
        List<String> publicAddresses = local.candidates().stream().filter(PublicAddress::ipv4).toList();
        if (!publicAddresses.isEmpty()) return new InternetExposure(local, publicAddresses, local.port(), null);
        UpnpPortMapper.Mapping mapping = UpnpPortMapper.open(local.port(), cancelled, lost);
        return new InternetExposure(local, List.of(mapping.address()), mapping.port(), mapping);
    }
    public Invite invitation(Invite current) {
        if (closed.get() || mapping != null && !mapping.valid()) throw new IllegalStateException("mapping unavailable");
        if (!partyId.equals(current.partyId()) || !fingerprint.equals(current.fingerprint())) throw new IllegalArgumentException("different host session");
        return new Invite(current.partyId(), addresses, port, current.fingerprint(), current.secret(), current.expiresAt());
    }
    public int advertisedPort() { return port; }
    public String mechanism() { return mapping == null ? "DIRECT_IPV4" : "UPNP"; }
    public boolean valid() { return !closed.get() && (mapping == null || mapping.valid()); }
    @Override public void close() { if (closed.compareAndSet(false, true) && mapping != null) mapping.close(); }
}
