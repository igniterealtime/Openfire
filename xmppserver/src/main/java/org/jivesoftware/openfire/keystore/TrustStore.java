package org.jivesoftware.openfire.keystore;

import org.jivesoftware.util.CertificateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyStoreException;
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
public class TrustStore extends CertificateStore
{
    private static final Logger Log = LoggerFactory.getLogger( TrustStore.class );

    public TrustStore( CertificateStoreConfiguration configuration, boolean createIfAbsent ) throws CertificateStoreConfigException
    {
        super( configuration, createIfAbsent );
    }

    /**
     * Imports one certificate as a trust anchor into this store.
     *
     * Note that this method explicitly allows one to add invalid certificates.
     *
     * As this store is intended to contain certificates for "most-trusted" / root Certificate Authorities, this method
     * will fail when the PEM representation contains more than one certificate.
     *
     * @param alias the name (key) under which the certificate is to be stored in the store (cannot be null or empty).
     * @param pemRepresentation The PEM representation of the certificate to add (cannot be null or empty).
     * @throws CertificateStoreConfigException if a single certificate could not be found
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
            throw new CertificateStoreConfigException( "Unable to install a certificate into a trust store.", e );
        }
        finally
        {
            reload(); // re-initialize store.
        }
    }

    /**
     * Decide whether or not to trust the given supplied certificate chain. For certain failures, we SHOULD generate
     * an exception - revocations and the like, but we currently do not.
     *
     * @param chain an array of X509Certificate where the first one is the endEntityCertificate.
     * @return true if the content of this trust store allows the chain to be trusted, otherwise false.
     */
    public boolean isTrusted( Certificate chain[] )
    {
        return getEndEntityCertificate( chain ) != null;
    }

    /**
     * Decide whether or not to trust the given supplied certificate chain, returning the
     * End Entity Certificate in this case where it can, and null otherwise.
     * A self-signed certificate will, for example, return null.
     * For certain failures, we SHOULD generate an exception - revocations and the like,
     * but we currently do not.
     *
     * @param chain an array of X509Certificate where the first one is the endEntityCertificate.
     * @return trusted end-entity certificate, or null.
     */
    public X509Certificate getEndEntityCertificate( Certificate chain[] )
    {
        if ( chain == null || chain.length == 0 )
        {
            return null;
        }

        final X509Certificate first = (X509Certificate) chain[ 0 ];
        try
        {
            first.checkValidity();
        }
        catch ( CertificateException e )
        {
            Log.warn( "EE Certificate not valid: " + e.getMessage() );
            return null;
        }

        if ( chain.length == 1 && first.getSubjectX500Principal().equals( first.getIssuerX500Principal() ) )
        {
            // Chain is single cert, and self-signed.
            try
            {
                if ( store.getCertificateAlias( first ) != null )
                {
                    // Interesting case: trusted self-signed cert.
                    return first;
                }
            }
            catch ( KeyStoreException e )
            {
                Log.warn( "Keystore error while looking for self-signed cert; assuming untrusted." );
            }
            return null;
        }

        final List<Certificate> allCerts = new ArrayList<>();
        try
        {
            // Add the trusted certs.
            for ( Enumeration<String> aliases = store.aliases(); aliases.hasMoreElements(); )
            {
                String alias = aliases.nextElement();
                if ( store.isCertificateEntry( alias ) )
                {
                    X509Certificate cert = (X509Certificate) store.getCertificate( alias );
                    allCerts.add( cert );
                }
            }

            // Finally, add all the certs in the chain:
            allCerts.addAll( Arrays.asList( chain ) );

            final CertStore cs = CertStore.getInstance( "Collection", new CollectionCertStoreParameters( allCerts ) );
            final X509CertSelector selector = new X509CertSelector();
            selector.setCertificate( first );
            // / selector.setSubject(first.getSubjectX500Principal());

            final PKIXBuilderParameters params = new PKIXBuilderParameters( store, selector );
            params.addCertStore( cs );
            params.setDate( new Date() );
            params.setRevocationEnabled( false );

            /* Code here is the right way to do things. */
            final CertPathBuilder pathBuilder = CertPathBuilder.getInstance( CertPathBuilder.getDefaultType() );
            final CertPath cp = pathBuilder.build( params ).getCertPath();

            /*
             * This section is an alternative to using CertPathBuilder which is not as complete (or safe), but will emit
             * much better errors. If things break, swap around the code.
             *
             **** COMMENTED OUT. ****
             *
            final List<X509Certificate> ls = new ArrayList<>();
            for ( final Certificate cert : chain )
            {
                ls.add( (X509Certificate) cert );
            }

            for ( X509Certificate last = ls.get( ls.size() - 1 );
                  !last.getIssuerX500Principal().equals( last.getSubjectX500Principal() );
                  last = ls.get( ls.size() - 1 ) )
            {
                final X509CertSelector sel = new X509CertSelector();
                sel.setSubject( last.getIssuerX500Principal() );
                ls.add( (X509Certificate) cs.getCertificates( sel ).toArray()[ 0 ] );
            }
            final CertPath cp = CertificateFactory.getInstance( "X.509" ).generateCertPath( ls );
             ****** END ALTERNATIVE. ****
             */

            // Not entirely sure if I need to do this with CertPathBuilder. Can't hurt.
            final CertPathValidator pathValidator = CertPathValidator.getInstance( "PKIX" );
            pathValidator.validate( cp, params );

            return (X509Certificate) cp.getCertificates().get( 0 );
        }
        catch ( CertPathBuilderException e )
        {
            Log.warn( "Path builder exception while validating certificate chain:", e );
        }
        catch ( CertPathValidatorException e )
        {
            Log.warn( "Path exception while validating certificate chain:", e );
        }
        catch ( Exception e )
        {
            Log.warn( "Unknown exception while validating certificate chain:", e );
        }
        return null;
    }

}
