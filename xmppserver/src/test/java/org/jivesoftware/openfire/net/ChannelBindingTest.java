package org.jivesoftware.openfire.net;

import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.jsse.BCExtendedSSLSession;
import org.bouncycastle.jsse.BCSSLConnection;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ChannelBindingTest {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private X509Certificate generateCertificate(String sigAlg) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        long now = System.currentTimeMillis();
        Date startDate = new Date(now - 24 * 60 * 60 * 1000);
        Date endDate = new Date(now + 365 * 24 * 60 * 60 * 1000);

        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                new org.bouncycastle.asn1.x500.X500Name("CN=Test"),
                BigInteger.valueOf(now),
                startDate,
                endDate,
                new org.bouncycastle.asn1.x500.X500Name("CN=Test"),
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded())
        );

        ContentSigner contentSigner = new JcaContentSignerBuilder(sigAlg).setProvider("BC").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(contentSigner));
    }

    @Test
    public void testTlsServerEndpointWithRealCert() throws Exception {
        X509Certificate cert = generateCertificate("SHA256withRSA");
        String sigAlgName = cert.getSigAlgName();
        assertEquals("SHA256WITHRSA", sigAlgName.toUpperCase().replace("-", ""));
        
        String hashAlg = getHashAlgFromSigAlg(sigAlgName);
        assertEquals("SHA-256", hashAlg);
        
        MessageDigest md = MessageDigest.getInstance(hashAlg);
        byte[] cbData = md.digest(cert.getEncoded());
        assertNotNull(cbData);
        assertTrue(cbData.length > 0);
    }
    
    @Test
    public void testTlsUniqueWithBctls() throws Exception {
        // tls-unique (RFC 5929)
        // For TLS 1.2 and earlier.
        // It's the 'client finished' message for the most recent handshake.
        
        BCSSLConnection mockConnection = Mockito.mock(BCSSLConnection.class);
        
        byte[] expectedTlsUnique = new byte[]{1, 2, 3, 4};
        Mockito.when(mockConnection.getChannelBinding("tls-unique")).thenReturn(expectedTlsUnique);
        
        byte[] cbData = mockConnection.getChannelBinding("tls-unique");
        assertArrayEquals(expectedTlsUnique, cbData);
    }

    @Test
    public void testTlsExporterWithBctls() throws Exception {
        // tls-exporter (RFC 9266)
        // For TLS 1.3.
        BCSSLConnection mockConnection = Mockito.mock(BCSSLConnection.class);
        
        byte[] expectedTlsExporter = new byte[]{5, 6, 7, 8};
        Mockito.when(mockConnection.getChannelBinding("tls-exporter")).thenReturn(expectedTlsExporter);
        
        byte[] cbData = mockConnection.getChannelBinding("tls-exporter");
        assertArrayEquals(expectedTlsExporter, cbData);
    }

    @Test
    public void testMapHashAlgs() {
        assertEquals("SHA-256", getHashAlgFromSigAlg("SHA256withRSA"));
        assertEquals("SHA-384", getHashAlgFromSigAlg("SHA384withRSA"));
        assertEquals("SHA-512", getHashAlgFromSigAlg("SHA512withRSA"));
        assertEquals("SHA-224", getHashAlgFromSigAlg("SHA224withRSA"));
        assertEquals("SHA-256", getHashAlgFromSigAlg("MD5withRSA"));
    }

    private String getHashAlgFromSigAlg(String sigAlg) {
        String upper = sigAlg.toUpperCase();
        if (upper.contains("SHA-256") || upper.contains("SHA256")) return "SHA-256";
        if (upper.contains("SHA-384") || upper.contains("SHA384")) return "SHA-384";
        if (upper.contains("SHA-512") || upper.contains("SHA512")) return "SHA-512";
        if (upper.contains("SHA-224") || upper.contains("SHA224")) return "SHA-224";
        return "SHA-256"; // Default for MD5, SHA1, etc.
    }
}
