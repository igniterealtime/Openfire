package org.jivesoftware.openfire.keystore;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jivesoftware.openfire.net.ClientTrustManager;
import org.jivesoftware.openfire.net.ServerTrustManager;
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

    private transient TrustManager[] trustManagers;

    private boolean acceptSelfSigned;
    private boolean checkValidity;

    public TrustStoreConfig( String path, String password, String type, boolean createIfAbsent, boolean acceptSelfSigned, boolean checkValidity ) throws CertificateStoreConfigException
    {
        super( path, password, type, createIfAbsent );
        this.acceptSelfSigned = acceptSelfSigned;
        this.checkValidity = checkValidity;
    }

    public synchronized TrustManager[] getTrustManagers() throws KeyStoreException, NoSuchAlgorithmException
    {
        if ( trustManagers == null ) {
            trustManagers = new TrustManager[] { new OpenfireX509ExtendedTrustManager( this.getStore(), acceptSelfSigned, checkValidity ) };
        }
        return trustManagers;
    }

    public synchronized void reconfigure( boolean acceptSelfSigned, boolean checkValidity ) throws CertificateStoreConfigException
    {
        boolean needsReload = false;
        if ( this.acceptSelfSigned != acceptSelfSigned )
        {
            this.acceptSelfSigned = acceptSelfSigned;
            needsReload = true;
        }

        if ( this.checkValidity != checkValidity )
        {
            this.checkValidity = checkValidity;
            needsReload = true;
        }

        if ( needsReload ) {
            reload();
        }
    }

    public boolean isAcceptSelfSigned()
    {
        return acceptSelfSigned;
    }

    public boolean isCheckValidity()
    {
        return checkValidity;
    }

    @Override
    public synchronized void reload() throws CertificateStoreConfigException
    {
        super.reload();
        trustManagers = null;
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

        // TODO Notify listeners that a new certificate has been added.
    }
}
