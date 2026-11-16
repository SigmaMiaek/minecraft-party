package pl.minecraftparty.core;

import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.*;

/** Ephemeral per-party certificate with out-of-band SHA-256 pinning. Uses public JCA APIs only. */
public final class TlsIdentity {
    private final SSLContext context;
    private final X509Certificate certificate;
    private TlsIdentity(SSLContext context, X509Certificate certificate) { this.context = context; this.certificate = certificate; }
    public SSLContext context() { return context; }
    public String fingerprint() {
        try { return fingerprint(certificate); } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    X509Certificate certificate() { return certificate; }
    public static TlsIdentity create() throws GeneralSecurityException, IOException {
        var generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1"));
        var keys = generator.generateKeyPair();
        byte[] algorithm = seq(der(6, HexFormat.of().parseHex("2a8648ce3d040302"))); // ecdsa-with-SHA256
        byte[] name = seq(der(0x31, seq(der(6, new byte[]{85, 4, 3}), der(12, "Minecraft Party".getBytes(StandardCharsets.UTF_8)))));
        var timeFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);
        Instant now = Instant.now();
        byte[] validity = seq(der(24, timeFormat.format(now.minusSeconds(300)).getBytes(StandardCharsets.US_ASCII)),
                der(24, timeFormat.format(now.plusSeconds(7 * 86400)).getBytes(StandardCharsets.US_ASCII)));
        byte[] tbs = seq(der(0xA0, der(2, new byte[]{2})), der(2, new BigInteger(120, new SecureRandom()).add(BigInteger.ONE).toByteArray()),
                algorithm, name, validity, name, keys.getPublic().getEncoded());
        var signature = Signature.getInstance("SHA256withECDSA"); signature.initSign(keys.getPrivate()); signature.update(tbs);
        byte[] bits = concat(new byte[]{0}, signature.sign());
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(seq(tbs, algorithm, der(3, bits))));
        cert.checkValidity(); cert.verify(keys.getPublic());
        char[] password = Secrets.token().toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12"); store.load(null, password);
        store.setKeyEntry("party", keys.getPrivate(), password, new java.security.cert.Certificate[]{cert});
        var manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); manager.init(store, password);
        Arrays.fill(password, '\0');
        SSLContext context = SSLContext.getInstance("TLSv1.3"); context.init(manager.getKeyManagers(), null, new SecureRandom());
        return new TlsIdentity(context, cert);
    }
    public static SSLContext client(String fingerprint) throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(null, new TrustManager[]{pinnedTrust(fingerprint)}, new SecureRandom());
        return context;
    }
    static X509TrustManager pinnedTrust(String pin) {
        if (!pin.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("pin");
        return new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException { throw new CertificateException("client certificate unsupported"); }
            public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                try {
                    if (chain == null || chain.length != 1 || !Secrets.equal(pin, fingerprint(chain[0])))
                        throw new CertificateException("pin mismatch");
                    chain[0].checkValidity(); chain[0].verify(chain[0].getPublicKey());
                } catch (GeneralSecurityException e) { throw new CertificateException("untrusted party", e); }
            }
        };
    }
    private static String fingerprint(X509Certificate cert) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
    }
    private static byte[] seq(byte[]... parts) { return der(0x30, concat(parts)); }
    private static byte[] concat(byte[]... parts) {
        var bytes = new ByteArrayOutputStream();
        for (byte[] part : parts) bytes.writeBytes(part);
        return bytes.toByteArray();
    }
    private static byte[] der(int tag, byte[] body) {
        var bytes = new ByteArrayOutputStream(); bytes.write(tag);
        if (body.length < 128) bytes.write(body.length);
        else {
            int size = (32 - Integer.numberOfLeadingZeros(body.length) + 7) / 8;
            bytes.write(0x80 | size);
            for (int i = size - 1; i >= 0; i--) bytes.write(body.length >>> (8 * i));
        }
        bytes.writeBytes(body); return bytes.toByteArray();
    }
}
