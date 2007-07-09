package org.jivesoftware.util;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Interface to listen for certificate events. Use
 * the {@link CertificateManager#addListener(CertificateEventListener)} method to register for events.
 *
 * @author Gaston Dombiak
 */
public interface CertificateEventListener {
    /**
     * Event triggered when a new certificate is created.
     *
     * @param keyStore key store where the certificate has been added. 
     * @param alias the alias of the certificate in the keystore.
     * @param cert the new certificate created.
     */
    void certificateCreated(KeyStore keyStore, String alias, X509Certificate cert);

    /**
     * Event triggered when a certificate is being deleted from the keystore.
     *
     * @param keyStore key store where the certificate is being deleted.
     * @param alias the alias of the certificate in the keystore.
     */
    void certificateDeleted(KeyStore keyStore, String alias);

    /**
     * Event triggered when a certificate has been signed by a Certificate Authority.
     *
     * @param keyStore key store where the certificate is stored.
     * @param alias the alias of the certificate in the keystore.
     * @param certificates chain of certificates. First certificate in the list is the certificate
     *        being signed and last certificate in the list is the root certificate.
     */
    void certificateSigned(KeyStore keyStore, String alias, List<X509Certificate> certificates);
}
