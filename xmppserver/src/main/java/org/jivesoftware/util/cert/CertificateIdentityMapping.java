package org.jivesoftware.util.cert;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * This is the interface used to map identity credentials from certificates.
 * Users may implement this class to map authentication credentials (i.e. usernames)
 * from certificate data (e.g. CommonName or SubjectAlternativeName) 
 * 
 * @author Victor Hong
 *
 */
public interface CertificateIdentityMapping {
    /**
     * Maps identities from X509Certificates
     * 
     * @param certificate The certificate from which to map identities
     * @return A list of identities mapped from the certificate 
     */
    List<String> mapIdentity(X509Certificate certificate);
    
    /**
     * Returns the short name of the mapping
     * 
     * @return The short name of the mapping
     */
    String name();
}
