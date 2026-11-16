package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import javax.net.ssl.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import static org.junit.jupiter.api.Assertions.*;

class TlsTest {
    @Test void certificatePinningRejectsDifferentIdentities() throws Exception {
        var host = TlsIdentity.create(); var other = TlsIdentity.create();
        var trust = TlsIdentity.pinnedTrust(host.fingerprint());
        trust.checkServerTrusted(new X509Certificate[]{host.certificate()}, "EC");
        assertThrows(CertificateException.class, () -> trust.checkServerTrusted(new X509Certificate[]{other.certificate()}, "EC"));
        host.certificate().verify(host.certificate().getPublicKey());
    }
    @Test void tls13HandshakeAndEncryptedPayloadWorkInMemoryWithoutSockets() throws Exception {
        var identity = TlsIdentity.create();
        SSLEngine client = TlsIdentity.client(identity.fingerprint()).createSSLEngine(); client.setUseClientMode(true);
        SSLEngine server = identity.context().createSSLEngine(); server.setUseClientMode(false);
        client.setEnabledProtocols(new String[]{"TLSv1.3"}); server.setEnabledProtocols(new String[]{"TLSv1.3"});
        client.beginHandshake(); server.beginHandshake();
        ByteBuffer clientToServer = ByteBuffer.allocate(131072), serverToClient = ByteBuffer.allocate(131072);
        ByteBuffer scratch = ByteBuffer.allocate(131072);
        for (int i = 0; i < 100; i++) {
            step(client, serverToClient, clientToServer, scratch);
            step(server, clientToServer, serverToClient, scratch);
            if (client.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
                    && server.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) break;
        }
        assertEquals("TLSv1.3", client.getSession().getProtocol());
        assertEquals(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, server.getHandshakeStatus());
        byte[] payload = "Minecraft Party encrypted payload".getBytes(StandardCharsets.UTF_8);
        clientToServer.clear(); client.wrap(ByteBuffer.wrap(payload), clientToServer);
        assertFalse(new String(clientToServer.array(), 0, clientToServer.position(), StandardCharsets.ISO_8859_1).contains("Minecraft Party"));
        clientToServer.flip(); scratch.clear(); server.unwrap(clientToServer, scratch); scratch.flip();
        byte[] received = new byte[scratch.remaining()]; scratch.get(received); assertArrayEquals(payload, received);
    }
    private static void step(SSLEngine engine, ByteBuffer incoming, ByteBuffer outgoing, ByteBuffer scratch) throws Exception {
        switch (engine.getHandshakeStatus()) {
            case NEED_TASK -> { Runnable task; while ((task = engine.getDelegatedTask()) != null) task.run(); }
            case NEED_WRAP -> engine.wrap(ByteBuffer.allocate(0), outgoing);
            case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> {
                incoming.flip(); scratch.clear(); engine.unwrap(incoming, scratch); incoming.compact();
            }
            default -> { }
        }
    }
}
