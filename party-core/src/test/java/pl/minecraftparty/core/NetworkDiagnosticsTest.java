package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import javax.net.ssl.SSLHandshakeException;
import java.io.*;
import java.net.*;
import java.security.cert.*;
import static org.junit.jupiter.api.Assertions.*;

class NetworkDiagnosticsTest {
    @Test void relayTlsFailuresDoNotClaimTheConnectionAlreadyReachedTheHost() {
        var timeout = NetworkFailure.from(NetworkFailure.Phase.TLS, new SocketTimeoutException());
        assertEquals("relay_tls_timeout", timeout.key(true)); assertEquals("tls_timeout", timeout.key(false));
        assertEquals("relay_tls_error", NetworkFailure.from(NetworkFailure.Phase.TLS, new SSLHandshakeException("ignored")).key(true));
        var certificate = new SSLHandshakeException("ignored"); certificate.initCause(new CertificateException());
        assertEquals("tls_certificate", NetworkFailure.from(NetworkFailure.Phase.TLS, certificate).key(true));
        assertEquals("tcp_timeout", NetworkFailure.from(NetworkFailure.Phase.TCP, new SocketTimeoutException()).key(true));
    }
    @Test void timeoutsAreAttributedToTheStageThatActuallyFailed() {
        assertEquals(NetworkFailure.Reason.TCP_TIMEOUT, NetworkFailure.from(NetworkFailure.Phase.TCP, new SocketTimeoutException()).reason());
        assertEquals(NetworkFailure.Reason.TLS_TIMEOUT, NetworkFailure.from(NetworkFailure.Phase.TLS, new SocketTimeoutException()).reason());
        assertEquals(NetworkFailure.Reason.LOBBY_TIMEOUT, NetworkFailure.from(NetworkFailure.Phase.LOBBY, new SocketTimeoutException()).reason());
    }
    @Test void tcpRefusalAndMissingRouteAreDistinct() {
        assertEquals("tcp_refused", NetworkFailure.from(NetworkFailure.Phase.TCP, new ConnectException()).key());
        assertEquals("no_route", NetworkFailure.from(NetworkFailure.Phase.TCP, new NoRouteToHostException()).key());
        assertEquals("lobby_closed", NetworkFailure.from(NetworkFailure.Phase.LOBBY, new EOFException()).key());
    }
    @Test void certificateMismatchAndClockProblemsAreNotReportedAsUnreachable() {
        var untrusted = new SSLHandshakeException("ignored"); untrusted.initCause(new CertificateException("pin mismatch"));
        assertEquals(NetworkFailure.Reason.TLS_CERTIFICATE, NetworkFailure.from(NetworkFailure.Phase.TLS, untrusted).reason());
        for (var cause : new CertificateException[]{new CertificateExpiredException(), new CertificateNotYetValidException()}) {
            var expired = new SSLHandshakeException("ignored"); expired.initCause(cause);
            assertEquals(NetworkFailure.Reason.TLS_CLOCK, NetworkFailure.from(NetworkFailure.Phase.TLS, expired).reason());
        }
    }
    @Test void laterTcpFailuresCannotHideAnEarlierTlsFailure() {
        var tls = NetworkFailure.from(NetworkFailure.Phase.TLS, new SSLHandshakeException("ignored"));
        var tcp = NetworkFailure.from(NetworkFailure.Phase.TCP, new SocketTimeoutException());
        assertSame(tls, NetworkFailure.prefer(tls, tcp));
        assertSame(tls, NetworkFailure.from(NetworkFailure.Phase.LOBBY, tls));
    }
    @Test void diagnosticTraceNeverCopiesRawExceptionMessagesOrInviteData() {
        String secret = "mp2:secret-credentials 203.0.113.20:25565";
        var failure = NetworkFailure.from(NetworkFailure.Phase.TCP, new ConnectException(secret));
        var trace = new ConnectionTrace(); trace.failure(1, failure);
        assertFalse(failure.toString().contains(secret)); assertFalse(trace.report().contains("203.0.113"));
        assertFalse(trace.report().contains("mp2:")); assertTrue(trace.report().contains("TCP_REFUSED"));
        for (int i = 0; i < 100; i++) trace.mark(1, NetworkFailure.Phase.TCP, ConnectionTrace.Event.CONNECTING);
        assertEquals(32, trace.report().lines().count());
    }
    @Test void hamachiRadminAndTunnelAdaptersAreNotDirectInternetCandidates() {
        assertFalse(InterfaceSelection.eligible("ham0", "ham0", false, false));
        assertFalse(InterfaceSelection.eligible("eth2", "LogMeIn Hamachi Virtual Ethernet Adapter", false, false));
        assertFalse(InterfaceSelection.eligible("Ethernet 4", "Radmin VPN", false, false));
        assertFalse(InterfaceSelection.eligible("wg0", "WireGuard", true, false));
        assertFalse(InterfaceSelection.eligible("tun0", "tun0", false, false));
        assertFalse(InterfaceSelection.eligible("ztabc123", "ZeroTier", false, false));
        assertFalse(InterfaceSelection.eligible("tailscale0", "tailscale0", false, false));
    }
    @Test void wiredWifiAndPhysicalPublicAddressInterfacesRemainEligible() {
        assertTrue(InterfaceSelection.eligible("eno2", "eno2", false, false));
        assertTrue(InterfaceSelection.eligible("wlo1", "wlo1", false, false));
        assertTrue(InterfaceSelection.eligible("Ethernet", "Intel Ethernet Adapter", false, false));
        assertTrue(InterfaceSelection.eligible("br0", "br0", false, false));
        assertFalse(InterfaceSelection.eligible("eth0:1", "eth0:1", false, true));
        assertFalse(InterfaceSelection.eligible("ppp0", "ppp0", true, false));
    }
}
