package pl.minecraftparty.core;

import java.io.*;
import java.net.*;
import java.security.cert.*;
import java.util.Locale;

/** User-safe failure classification. Exception messages and endpoint addresses are deliberately discarded. */
public final class NetworkFailure extends IOException {
    public enum Phase { TCP, TLS, LOBBY }
    public enum Reason { TCP_TIMEOUT, TCP_REFUSED, NO_ROUTE, NETWORK_ERROR, TLS_TIMEOUT, TLS_CERTIFICATE,
        TLS_CLOCK, TLS_ERROR, LOBBY_TIMEOUT, LOBBY_CLOSED, LOBBY_PROTOCOL }
    private final Phase phase;
    private final Reason reason;
    private final String exceptionType;
    private NetworkFailure(Phase phase, Reason reason, String exceptionType) {
        super("Party " + phase + ": " + reason); this.phase = phase; this.reason = reason; this.exceptionType = exceptionType;
    }
    public Phase phase() { return phase; }
    public Reason reason() { return reason; }
    public String key() { return reason.name().toLowerCase(Locale.ROOT); }
    public String key(boolean throughRelay) {
        if (throughRelay && reason == Reason.TLS_TIMEOUT) return "relay_tls_timeout";
        if (throughRelay && reason == Reason.TLS_ERROR) return "relay_tls_error";
        return key();
    }
    public String exceptionType() { return exceptionType; }
    public static NetworkFailure from(Phase phase, IOException error) {
        if (error instanceof NetworkFailure failure) return failure;
        Reason reason;
        if (error instanceof SocketTimeoutException)
            reason = phase == Phase.TCP ? Reason.TCP_TIMEOUT : phase == Phase.TLS ? Reason.TLS_TIMEOUT : Reason.LOBBY_TIMEOUT;
        else if (phase == Phase.TLS) {
            boolean certificate = false, clock = false;
            Throwable current = error;
            for (int i = 0; i < 12 && current != null; i++, current = current.getCause()) {
                certificate |= current instanceof CertificateException;
                clock |= current instanceof CertificateExpiredException || current instanceof CertificateNotYetValidException;
            }
            reason = clock ? Reason.TLS_CLOCK : certificate ? Reason.TLS_CERTIFICATE : Reason.TLS_ERROR;
        } else if (phase == Phase.TCP && error instanceof NoRouteToHostException) reason = Reason.NO_ROUTE;
        else if (phase == Phase.TCP && error instanceof ConnectException) reason = Reason.TCP_REFUSED;
        else if (phase == Phase.LOBBY && (error instanceof EOFException || error instanceof SocketException)) reason = Reason.LOBBY_CLOSED;
        else reason = phase == Phase.LOBBY ? Reason.LOBBY_PROTOCOL : Reason.NETWORK_ERROR;
        return new NetworkFailure(phase, reason, error.getClass().getSimpleName());
    }
    public static NetworkFailure prefer(NetworkFailure previous, NetworkFailure next) {
        if (previous == null || next.phase.ordinal() >= previous.phase.ordinal()) return next;
        return previous;
    }
}
