package org.jivesoftware.openfire.keystore;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jivesoftware.util.CertificateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.*;
import java.util.*;

/**
 * A wrapper class for a store of certificates, its metadata (password, location) and related functionality that is
 * used to <em>verify</em> credentials, a <em>trust store</em>
 *
 * The trust store should only contain certificates for the "most-trusted" Certificate Authorities (the store should not
 * contain Intermediates"). These certificates are referred to as "Trust Anchors".
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class TrustStoreConfig extends CertificateStoreConfig
{
    private static final Logger Log = LoggerFactory.getLogger( TrustStoreConfig.class );

    private final TrustManagerFactory trustFactory;

    private final CertPathValidator certPathValidator; // not thread safe
    private final CertificateFactory certificateFactory; // not thread safe.

    public TrustStoreConfig( String path, String password, String type, boolean createIfAbsent ) throws CertificateStoreConfigException
    {
        super( path, password, type, createIfAbsent );

        try
        {
            certPathValidator = CertPathValidator.getInstance( "PKIX" );
            certificateFactory = CertificateFactory.getInstance( "X.509" );
            trustFactory = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
            trustFactory.init( store );
        }
        catch ( CertificateException | NoSuchAlgorithmException | KeyStoreException ex )
        {
            throw new CertificateStoreConfigException( "Unable to load store of type '" + type + "' from location '" + path + "'", ex );
        }
    }

    public TrustManager[] getTrustManagers()
    {
        return trustFactory.getTrustManagers();
    }

    /**
     * Returns all valid certificates from the store.
     *
     * @return A collection of certificates (possibly empty, but never null).
     */
    protected Set<TrustAnchor> getAllValidTrustAnchors() throws KeyStoreException
    {
        final Set<TrustAnchor> results = new HashSet<>();

        for ( X509Certificate certificate : getAllCertificates().values() )
        {
            try
            {
                certificate.checkValidity();
            }
            catch ( CertificateExpiredException | CertificateNotYetValidException e )
            {
                // Not yet or no longer valid. Don't include in result.
                continue;
            }

            final TrustAnchor trustAnchor = new TrustAnchor( certificate, null );
            results.add( trustAnchor );
        }

        return results;
    }

    /**
     * Validates the provided certificate chain, by verifying (among others):
     * <ul>
     *     <li>The validity of each certificate in the chain</li>
     *     <li>chain integrity (matching issuer/subject)</li>
     *     <li>the root of the chain is validated by a trust anchor that is in this store.</li>
     * </ul>
     *
     * @param chain A chain of certificates (cannot be null)
     * @return true when the validity of the chain could be verified, otherwise false.
     */
    public synchronized boolean canTrust( Collection<X509Certificate> chain )
    {
        // Input validation
        if ( chain == null )
        {
            throw new IllegalArgumentException( "Argument 'chain' cannot be null." );
        }

        if (chain.isEmpty() )
        {
            return false;
        }

        // For some reason, the default validation fails to iterate over all providers and will fail if the default
        // provider does not support the algorithm of the chain. To work around this issue, this code iterates over
        // each provider explicitly, returning success when at least one provider validates the chain successfully.
        Log.debug( "Iterating over all available security providers in order to validate a certificate chain." );
        for (Provider p : Security.getProviders())
        {
            try
            {
                final Set<TrustAnchor> trustAnchors = getAllValidTrustAnchors();
                final CertPath certPath = getCertPath( chain );

                final PKIXParameters parameters = new PKIXParameters( trustAnchors );
                parameters.setRevocationEnabled( false ); // TODO: enable revocation list validation.
                parameters.setSigProvider( p.getName() ); // Explicitly iterate over each signature provider. See comment above.

                certPathValidator.validate( certPath, parameters );

                Log.debug( "Provider "+p.getName()+": Able to validate certificate chain." );
                return true;
            }
            catch ( Exception ex )
            {
                Log.debug( "Provider "+p.getName()+": Unable to validate certificate chain.", ex );
            }
        }

        return false;
    }

    /**
     * Creates a CertPath instance from the provided certificate chain.
     *
     * This implementation can process unordered input (ordering will by applied).
     *
     * @param chain A certificate chain (cannot be null or an empty collection).
     * @return A CertPath instance (never null).
     * @throws CertificateException When no CertPath instance could be created.
     */
    protected synchronized CertPath getCertPath( Collection<X509Certificate> chain ) throws CertificateException
    {
        // Input validation
        if ( chain == null || chain.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'chain' cannot be null or empty." );
        }

        // Note that PKCS#7 does not require a specific order for the certificates in the file - ordering is needed.
        final List<X509Certificate> ordered = CertificateManager.order( chain );

        return certificateFactory.generateCertPath( ordered );
    }

    /**
     * Imports one certificate as a trust anchor into this store.
     *
     * Note that this method explicitly allows one to add invalid certificates. Other methods in this class might ignore
     * such a certificate ({@link #canTrust(Collection)} being a prime example).
     *
     * As this store is intended to contain certificates for "most-trusted" / root Certificate Authorities, this method
     * will fail when the PEM representation contains more than one certificate.
     *
     * @param alias the name (key) under which the certificate is to be stored in the store (cannot be null or empty).
     * @param pemRepresentation The PEM representation of the certificate to add (cannot be null or empty).
     */
    public void installCertificate( String alias, String pemRepresentation ) throws CertificateStoreConfigException
    {
        // Input validation
        if ( alias == null || alias.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'alias' cannot be null or an empty String." );
        }
        if ( pemRepresentation == null )
        {
            throw new IllegalArgumentException( "Argument 'pemRepresentation' cannot be null." );
        }
        alias = alias.trim();

        // Check that there is a certificate for the specified alias
        try
        {
            if ( store.containsAlias( alias ) )
            {
                throw new CertificateStoreConfigException( "Certificate already exists for alias: " + alias );
            }

            // From their PEM representation, parse the certificates.
            final Collection<X509Certificate> certificates = CertificateManager.parseCertificates( pemRepresentation );

            if ( certificates.isEmpty() ) {
                throw new CertificateStoreConfigException( "No certificate was found in the input.");
            }
            if ( certificates.size() != 1 ) {
                throw new CertificateStoreConfigException( "More than one certificate was found in the input." );
            }

            final X509Certificate certificate = certificates.iterator().next();

            store.setCertificateEntry(alias, certificate);
            persist();
        }
        catch ( CertificateException | KeyStoreException | IOException e )
        {
            reload(); // reset state of the store.
            throw new CertificateStoreConfigException( "Unable to install a certificate into a trust store.", e );
        }

        // TODO Notify listeners that a new certificate has been added.
    }
}
