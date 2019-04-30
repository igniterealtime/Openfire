package org.jivesoftware.openfire.keystore;

import org.bouncycastle.operator.OperatorCreationException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.net.DNSUtil;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A wrapper class for a store of certificates, its metadata (password, location) and related functionality that is
 * used to <em>provide</em> credentials (that represent this Openfire instance), an <em>identity store</em>
 *
 * An identity store should contain private keys, each associated with its certificate chain.
 *
 * Having the root certificate of the Certificate Authority that signed the certificates in this identity store should
 * be in a corresponding trust store, although this is not strictly required. The reasoning here is that when you trust
 * a Certificate Authority to verify your identity, you're likely to trust the same Certificate Authority to verify the
 * identities of others.
 *
 * Note that in Java terminology, an identity store is commonly referred to as a 'key store', while the same name is
 * also used to identify the generic certificate store. To have clear distinction between common denominator and each of
 * the specific types, this implementation uses the terms "certificate store", "identity store" and "trust store".
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class IdentityStore extends CertificateStore
{
    private static final Logger Log = LoggerFactory.getLogger( IdentityStore.class );

    public IdentityStore( CertificateStoreConfiguration configuration, boolean createIfAbsent ) throws CertificateStoreConfigException
    {
        super( configuration, createIfAbsent );

        try
        {
            KeyManagerFactory keyManagerFactory;
            try
            {
                // OF-1501: If multiple certificates are available, the 'NewSunX509' implementation in the SunJSSE
                // provider makes the effort to pick a certificate with the appropriate key usage and prefers valid
                // to expired certificates.
                keyManagerFactory = KeyManagerFactory.getInstance( "NewSunX509" );
            }
            catch ( NoSuchAlgorithmException e )
            {
                Log.info( "Unable to load the 'NewSunX509' KeyManager implementation. Will fall back to the default." );
                keyManagerFactory = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
            }

            keyManagerFactory.init( this.getStore(), configuration.getPassword() );
        }
        catch ( NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException ex )
        {
            throw new CertificateStoreConfigException( "Unable to initialize identity store (a common cause: the password for a key is different from the password of the entire store).", ex );
        }

    }

    /**
     * Creates a Certificate Signing Request based on the private key and certificate identified by the provided alias.
     *
     * When the alias does not identify a private key and/or certificate, this method will throw an exception.
     *
     * The certificate that is identified by the provided alias can be an unsigned certificate, but also a certificate
     * that is already signed. The latter implies that the generated request is a request for certificate renewal.
     *
     * An invocation of this method does not change the state of the underlying store.
     *
     * @param alias An identifier for a private key / certificate in this store (cannot be null).
     * @return A PEM-encoded Certificate Signing Request (never null).
     * @throws CertificateStoreConfigException if there was a problem generating the CSR
     */
    public String generateCSR( String alias ) throws CertificateStoreConfigException
    {
        // Input validation
        if ( alias == null || alias.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'alias' cannot be null or an empty String." );
        }
        alias = alias.trim();

        try
        {
            if ( !store.containsAlias( alias ) ) {
                throw new CertificateStoreConfigException( "Cannot generate CSR for alias '"+ alias +"': the alias does not exist in the store." );
            }

            final Certificate certificate = store.getCertificate( alias );
            if ( certificate == null || (!(certificate instanceof X509Certificate)))
            {
                throw new CertificateStoreConfigException( "Cannot generate CSR for alias '"+ alias +"': there is no corresponding certificate in the store, or it is not an X509 certificate." );
            }

            final Key key = store.getKey( alias, configuration.getPassword() );
            if ( key == null || (!(key instanceof PrivateKey) ) )
            {
                throw new CertificateStoreConfigException( "Cannot generate CSR for alias '"+ alias +"': there is no corresponding key in the store, or it is not a private key." );
            }

            final String pemCSR = CertificateManager.createSigningRequest( (X509Certificate) certificate, (PrivateKey) key );

            return pemCSR;
        }
        catch ( IOException | KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException | OperatorCreationException | CertificateParsingException e )
        {
            throw new CertificateStoreConfigException( "Cannot generate CSR for alias '"+ alias +"'", e );
        }
    }

    /**
     * Imports a certificate (and its chain) in this store.
     *
     * This method will fail when the provided certificate chain:
     * <ul>
     *     <li>does not match the domain of this XMPP service.</li>
     *     <li>is not a proper chain</li>
     * </ul>
     *
     * This method will also fail when a corresponding private key is not already in this store (it is assumed that the
     * CA reply follows a signing request based on a private key that was added to the store earlier).
     *
     * @param alias the certificate alias
     * @param pemCertificates a PEM representation of the certificate or certificate chain (cannot be null or empty).
     * @throws CertificateStoreConfigException if there was a problem installing the certificate
     */
    public void installCSRReply( String alias, String pemCertificates ) throws CertificateStoreConfigException
    {
        // Input validation
        if ( alias == null || alias.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'alias' cannot be null or an empty String." );
        }
        if ( pemCertificates == null || pemCertificates.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'pemCertificates' cannot be null or an empty String." );
        }
        alias = alias.trim();
        pemCertificates = pemCertificates.trim();

        try
        {
            // From its PEM representation, parse the certificates.
            final Collection<X509Certificate> certificates = CertificateManager.parseCertificates( pemCertificates );
            if ( certificates.isEmpty() )
            {
                throw new CertificateStoreConfigException( "No certificate was found in the input." );
            }

            // Note that PKCS#7 does not require a specific order for the certificates in the file - ordering is needed.
            final List<X509Certificate> ordered = CertificateUtils.order( certificates );

            // Of the ordered chain, the first certificate should be for our domain.
            if ( !isForThisDomain( ordered.get( 0 ) ) )
            {
                throw new CertificateStoreConfigException( "The supplied certificate chain does not cover the domain of this XMPP service." );
            }

            // This method is used to update a pre-existing entry in the store. Find out if this entry corresponds with the provided certificate chain.
            if ( !corresponds( alias, ordered ) ) {
                throw new IllegalArgumentException( "The provided CSR reply does not match an existing certificate in the store under the provided alias '" + alias + "'." );
            }

            // All appears to be in order. Update the existing entry in the store.
            store.setKeyEntry( alias, store.getKey( alias, configuration.getPassword() ), configuration.getPassword(), ordered.toArray( new X509Certificate[ ordered.size() ] ) );
        }
        catch ( RuntimeException | IOException | CertificateException | UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e )
        {
            reload(); // reset state of the store.
            throw new CertificateStoreConfigException( "Unable to install a singing reply into an identity store.", e );
        }
        // TODO notifiy listeners.
    }

    protected boolean corresponds( String alias, List<X509Certificate> certificates ) throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException
    {
        if ( !store.containsAlias( alias ) ) {
            return false;
        }

        final Key key = store.getKey( alias, configuration.getPassword() );
        if ( key == null ) {
            return false;
        }

        if ( !(key instanceof PrivateKey)) {
            return false;
        }

        final Certificate certificate = store.getCertificate( alias );
        if ( certificate == null ) {
            return false;
        }

        if ( !(certificate instanceof X509Certificate) ) {
            return false;
        }

        final X509Certificate x509Certificate = (X509Certificate) certificate;

        // First certificate in the chain should correspond with the certificate in the store
        if ( !x509Certificate.getPublicKey().equals(certificates.get(0).getPublicKey()) )
        {
            return false;
        }

        return true;
    }

    /**
     * Imports a certificate and the private key that was used to generate the certificate, replacing any previously
     * installed entries for the same domain.
     *
     * This method will import the certificate and key in the store using a unique alias. This alias is returned.
     *
     * This method will fail when the provided certificate does not match the domain of this XMPP service.
     *
     * @param pemCertificates a PEM representation of the certificate or certificate chain (cannot be null or empty).
     * @param pemPrivateKey   a PEM representation of the private key (cannot be null or empty).
     * @param passPhrase      optional pass phrase (must be present if the private key is encrypted).
     * @return The alias that was used (never null).
     * @throws CertificateStoreConfigException if there was a problem replacing the certificate
     */
    public String replaceCertificate( String pemCertificates, String pemPrivateKey, String passPhrase ) throws CertificateStoreConfigException
    {
        if ( pemCertificates == null || pemCertificates.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'pemCertificates' cannot be null or an empty String." );
        }
        if ( pemPrivateKey == null || pemPrivateKey.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'pemPrivateKey' cannot be null or an empty String." );
        }
        pemCertificates = pemCertificates.trim();

        try
        {
            // From its PEM representation, parse the certificates.
            final Collection<X509Certificate> certificates = CertificateManager.parseCertificates( pemCertificates );
            if ( certificates.isEmpty() )
            {
                throw new CertificateStoreConfigException( "No certificate was found in the input." );
            }

            // Note that PKCS#7 does not require a specific order for the certificates in the file - ordering is needed.
            final List<X509Certificate> ordered = CertificateUtils.order( certificates );

            // Of the ordered chain, the first certificate should be for our domain.
            if ( !isForThisDomain( ordered.get( 0 ) ) )
            {
                throw new CertificateStoreConfigException( "The supplied certificate chain does not cover the domain of this XMPP service." );
            }

            // From its PEM representation (and pass phrase), parse the private key.
            final PrivateKey privateKey = CertificateManager.parsePrivateKey( pemPrivateKey, passPhrase );

            // All appears to be in order. Replace any entries in the store.
            removeAllDomainEntries();
            final String alias = generateUniqueAlias();
            store.setKeyEntry( alias, privateKey, configuration.getPassword(), ordered.toArray( new X509Certificate[ ordered.size() ] ) );

            persist();

            Log.info( "Replaced all private keys and corresponding certificate chains with a new private key and certificate chain." );

            return alias;
        }
        catch ( CertificateException | KeyStoreException | IOException e )
        {
            reload(); // reset state of the store.
            throw new CertificateStoreConfigException( "Unable to install a certificate into an identity store.", e );
        }

        // TODO Notify listeners that a new certificate has been replaced.
    }


    /**
     * Imports a certificate and the private key that was used to generate the certificate.
     *
     * This method will import the certificate and key in the store using a unique alias. This alias is returned.
     *
     * This method will fail when the provided certificate does not match the domain of this XMPP service.
     *
     * @param pemCertificates a PEM representation of the certificate or certificate chain (cannot be null or empty).
     * @param pemPrivateKey   a PEM representation of the private key (cannot be null or empty).
     * @param passPhrase      optional pass phrase (must be present if the private key is encrypted).
     * @return The alias that was used (never null).
     * @throws CertificateStoreConfigException if there was a problem installing the certificate
     */
    public String installCertificate( String pemCertificates, String pemPrivateKey, String passPhrase ) throws CertificateStoreConfigException
    {
        final String alias = generateUniqueAlias();

        // Perform the installation using the generated alias.
        installCertificate( alias, pemCertificates, pemPrivateKey, passPhrase );

        return alias;
    }

    /**
     * Imports a certificate and the private key that was used to generate the certificate.
     *
     * This method will fail when the provided certificate does not match the domain of this XMPP service, or when the
     * provided alias refers to an existing entry.
     *
     * @param alias           the name (key) under which the certificate is to be stored in the store (cannot be null or empty).
     * @param pemCertificates a PEM representation of the certificate or certificate chain (cannot be null or empty).
     * @param pemPrivateKey   a PEM representation of the private key (cannot be null or empty).
     * @param passPhrase      optional pass phrase (must be present if the private key is encrypted).
     * @throws CertificateStoreConfigException if there was a problem installing the certificate
     */
    public void installCertificate( String alias, String pemCertificates, String pemPrivateKey, String passPhrase ) throws CertificateStoreConfigException
    {
        // Input validation
        if ( alias == null || alias.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'alias' cannot be null or an empty String." );
        }
        if ( pemCertificates == null || pemCertificates.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'pemCertificates' cannot be null or an empty String." );
        }
        if ( pemPrivateKey == null || pemPrivateKey.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'pemPrivateKey' cannot be null or an empty String." );
        }
        alias = alias.trim();
        pemCertificates = pemCertificates.trim();

        // Check that there is a certificate for the specified alias
        try
        {
            if ( store.containsAlias( alias ) )
            {
                throw new CertificateStoreConfigException( "Certificate already exists for alias: " + alias );
            }

            // From its PEM representation, parse the certificates.
            final Collection<X509Certificate> certificates = CertificateManager.parseCertificates( pemCertificates );
            if ( certificates.isEmpty() )
            {
                throw new CertificateStoreConfigException( "No certificate was found in the input." );
            }

            // Note that PKCS#7 does not require a specific order for the certificates in the file - ordering is needed.
            final List<X509Certificate> ordered = CertificateUtils.order( certificates );

            // Of the ordered chain, the first certificate should be for our domain.
            if ( !isForThisDomain( ordered.get( 0 ) ) )
            {
                throw new CertificateStoreConfigException( "The supplied certificate chain does not cover the domain of this XMPP service." );
            }

            // From its PEM representation (and pass phrase), parse the private key.
            final PrivateKey privateKey = CertificateManager.parsePrivateKey( pemPrivateKey, passPhrase );

            // All appears to be in order. Install in the store.
            store.setKeyEntry( alias, privateKey, configuration.getPassword(), ordered.toArray( new X509Certificate[ ordered.size() ] ) );

            persist();

            Log.info( "Installed a new private key and corresponding certificate chain." );

        }
        catch ( CertificateException | KeyStoreException | IOException e )
        {
            reload(); // reset state of the store.
            throw new CertificateStoreConfigException( "Unable to install a certificate into an identity store.", e );
        }

        // TODO Notify listeners that a new certificate has been added.
    }

    /**
     * Adds a self-signed certificate for the domain of this XMPP service when no certificate for the domain was found.
     * @throws CertificateStoreConfigException if there was a problem creating the certificate
     */
    public synchronized void ensureDomainCertificate() throws CertificateStoreConfigException
    {
        Log.debug( "Verifying that a domain certificate is available in this store." );
        if ( !containsDomainCertificate() )
        {
            Log.debug( "Store does not contain a domain certificate. A self-signed certificate will be generated." );
            addSelfSignedDomainCertificate();
        }
    }

    /**
     * Adds a self-signed certificate for the domain of this XMPP service when no certificate for the domain (of the
     * provided algorithm) was found.
     *
     * This method is a thread-safe equivalent of:
     * <pre>
     *   for ( String algorithm : algorithms ) {
     *     if ( !containsDomainCertificate( algorithm ) ) {
     *        addSelfSignedDomainCertificate( algorithm );
     *     }
     *   }
     * </pre>
     *
     * @param algorithms The algorithms for which to verify / add a domain certificate.
     * @deprecated Unused as of Openfire 4.3.0. Use 'ensureDomainCertificate' instead. See OF-1599.
     * @throws CertificateStoreConfigException if there was a problem creating the certificate
     */
    @Deprecated
    public synchronized void ensureDomainCertificates( String... algorithms ) throws CertificateStoreConfigException
    {
        for ( String algorithm : algorithms )
        {
            Log.debug( "Verifying that a domain certificate ({} algorithm) is available in this store.", algorithm);
            if ( !containsDomainCertificate( algorithm ) )
            {
                Log.debug( "Store does not contain a domain certificate ({} algorithm). A self-signed certificate will be generated.", algorithm);
                addSelfSignedDomainCertificate( algorithm );
            }
        }
    }

    /**
     * Checks if the store contains a certificate of a particular algorithm that matches the domain of this
     * XMPP service. This method will not distinguish between self-signed and non-self-signed certificates.
     * @return {@code true}  if the store contains a certificate of a particular algorithm that matches the domain of this XMPP service, otherwise {@code false}
     * @throws CertificateStoreConfigException if there was a problem creating the certificate
     */
    @SuppressWarnings("deprecation")
    public synchronized boolean containsDomainCertificate() throws CertificateStoreConfigException
    {
        return containsDomainCertificate( null );
    }

    /**
     * Checks if the store contains a certificate of a particular algorithm that matches the domain of this
     * XMPP service. This method will not distinguish between self-signed and non-self-signed certificates.
     *
     * If the 'algorithm' parameter is used, then this method will evaluate only certificates that match that
     * certificate.
     *
     * @param algorithm An optional algorithm constraint (eg: "RSA"). Can be null, cannot be empty.
     * @return {@code true} if the store contains a certificate of a particular algorithm that matches the domain of this XMPP service, otherwise {@code false}
     * @deprecated Unused as of Openfire 4.3.0. Use 'containsDomainCertificate' instead. See OF-1599.
     * @throws CertificateStoreConfigException if there was a problem creating the certificate
     */
    @Deprecated
    public synchronized boolean containsDomainCertificate( String algorithm ) throws CertificateStoreConfigException
    {
        if ( algorithm != null && algorithm.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'algorithm' cannot be empty (but is allowed to be null)." );
        }

        final String domainName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

        try
        {
            for ( final String alias : Collections.list( store.aliases() ) )
            {
                final Certificate certificate = store.getCertificate( alias );
                if ( !( certificate instanceof X509Certificate ) )
                {
                    continue;
                }

                // Filter on algorithm, if the algorithm argument is defined.
                if ( algorithm != null && !certificate.getPublicKey().getAlgorithm().equalsIgnoreCase( algorithm ) )
                {
                    continue;
                }

                for ( String identity : CertificateManager.getServerIdentities( (X509Certificate) certificate ) )
                {
                    if ( DNSUtil.isNameCoveredByPattern( domainName, identity ) )
                    {
                        return true;
                    }
                }
            }
            return false;
        }
        catch ( KeyStoreException e )
        {
            throw new CertificateStoreConfigException( "An exception occurred while searching for " + algorithm + " certificates that match the Openfire domain.", e );
        }
    }

    /**
     * Checks if the store contains a certificate of a particular algorithm that contains at least all of the identities
     * of this server (which includes the XMPP domain name, but also its hostname, and XMPP addresses of components
     * that are currently being hosted).
     *
     * This method will not distinguish between self-signed and non-self-signed certificates.
     * @return {@code true} if the store contains a certificate of a particular algorithm that contains at least all of the identities of this server, otherwise {@code false}
     * @throws CertificateStoreConfigException if there was a problem accessing the certificates
     */
    @SuppressWarnings("deprecation")
    public synchronized boolean containsAllIdentityCertificate() throws CertificateStoreConfigException
    {
        return containsAllIdentityCertificate( null );
    }

    /**
     * Checks if the store contains a certificate of a particular algorithm that contains at least all of the identities
     * of this server (which includes the XMPP domain name, but also its hostname, and XMPP addresses of components
     * that are currently being hosted).
     *
     * This method will not distinguish between self-signed and non-self-signed certificates.
     *
     * If the 'algorithm' parameter is used, then this method will evaluate only certificates that match that
     * certificate.
     *
     * @param algorithm An optional algorithm constraint (eg: "RSA"). Can be null, cannot be empty.
     * @deprecated Unused as of Openfire 4.3.0. Use 'containsAllIdentityCertificate' instead. See OF-1599.
     * @throws CertificateStoreConfigException if a self-signed certificate could not be created
     * @return {{@code true} if a certiicate contains all identities for this server, otherwise {@code false}}
     */
    @Deprecated
    public synchronized boolean containsAllIdentityCertificate( String algorithm ) throws CertificateStoreConfigException
    {
        if ( algorithm != null && algorithm.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'algorithm' cannot be empty (but is allowed to be null)." );
        }
        final Collection<String> dns = CertificateManager.determineSubjectAlternateNameDnsNameValues();

        try
        {
            for ( final String alias : Collections.list( store.aliases() ) )
            {
                final Set<String> missingDns = new HashSet<>();

                final Certificate certificate = store.getCertificate( alias );
                if ( !( certificate instanceof X509Certificate ) )
                {
                    continue;
                }

                if ( algorithm != null && !certificate.getPublicKey().getAlgorithm().equalsIgnoreCase( algorithm ) )
                {
                    continue;
                }

                final List<String> serverIdentities = CertificateManager.getServerIdentities( (X509Certificate) certificate );

                // Are all of our DNS names covered?
                for ( String dnsId : dns )
                {
                    boolean found = false;
                    for ( String identity : serverIdentities )
                    {
                        if ( DNSUtil.isNameCoveredByPattern( dnsId, identity ) )
                        {
                            found = true;
                            break;
                        }
                    }

                    if ( !found )
                    {
                        Log.info( "Certificate with alias '{}' is missing DNS identity '{}'.", alias, dnsId );
                        missingDns.add( dnsId );
                    }
                }

                if ( missingDns.isEmpty() )
                {
                    return true;
                }
            }
            return false;
        }
        catch ( KeyStoreException e )
        {
            throw new CertificateStoreConfigException( "An exception occurred while searching for " + ( algorithm == null ? "" : algorithm + " " )+ "certificates that match the Openfire domain.", e );
        }
    }

    /**
     * Populates the key store with a self-signed certificate for the domain of this XMPP service.
     * @throws CertificateStoreConfigException if a self-signed certificate could not be created
     */
    @SuppressWarnings("deprecation")
    public synchronized void addSelfSignedDomainCertificate() throws CertificateStoreConfigException
    {
        addSelfSignedDomainCertificate( null );
    }

    /**
     * Populates the key store with a self-signed certificate for the domain of this XMPP service.
     *
     * If the 'algorithm' parameter is used, then this method will evaluate only certificates that match that
     * certificate.
     *
     * @param algorithm An optional algorithm constraint (eg: "RSA"). Can be null, cannot be empty.
     * @deprecated Unused as of Openfire 4.3.0. Use 'addSelfSignedDomainCertificate' instead. See OF-1599.
     * @throws CertificateStoreConfigException if a self-signed certificate could not be created
     */
    @Deprecated
    public synchronized void addSelfSignedDomainCertificate( String algorithm ) throws CertificateStoreConfigException
    {
        if ( algorithm != null && algorithm.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'algorithm' cannot be empty (but is allowed to be null)." );
        }

        final int keySize;
        final String signAlgorithm;

        if ( algorithm == null ) {
            algorithm = JiveGlobals.getProperty( "cert.algorithm", "RSA" );
        }
        switch ( algorithm.toUpperCase() )
        {
            case "RSA":
                keySize = JiveGlobals.getIntProperty( "cert.rsa.keysize", 2048 );
                signAlgorithm = JiveGlobals.getProperty( "cert.rsa.algorithm", "SHA256WITHRSAENCRYPTION" );
                break;

            case "DSA":
                keySize = JiveGlobals.getIntProperty( "cert.dsa.keysize", 1024 );
                signAlgorithm = JiveGlobals.getProperty( "cert.dsa.algorithm", "SHA256withDSA" );
                break;

            default:
                throw new IllegalArgumentException( "Unsupported algorithm '" + algorithm + "'. Use 'RSA' or 'DSA'." );
        }

        final String name = XMPPServerInfo.XMPP_DOMAIN.getValue().toLowerCase();
        final String alias = name + "_" + algorithm.toLowerCase();
        final int validityInDays = JiveGlobals.getIntProperty( "cert.validity-days", 5*365 );
        Set<String> sanDnsNames = CertificateManager.determineSubjectAlternateNameDnsNameValues();

        // OF-1605: Check if a wildcard entry is to be used to represent/replace any subdomains of the XMPP domain name.
        final boolean useWildcard = JiveGlobals.getBooleanProperty( "cert.wildcard", true );
        if ( useWildcard )
        {
            final String wildcard = "*." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();

            // Remove any names that match the wildcard.
            sanDnsNames = sanDnsNames.stream()
                .filter( sanDnsName -> !DNSUtil.isNameCoveredByPattern( sanDnsName, wildcard )  )
                .collect( Collectors.toSet() );

            // Add the domain and wildcard entries.
            sanDnsNames.add( XMPPServer.getInstance().getServerInfo().getXMPPDomain() );
            sanDnsNames.add( wildcard );
        }

        Log.info( "Generating a new private key and corresponding self-signed certificate for domain name '{}', using the {} algorithm (sign-algorithm: {} with a key size of {} bits). Certificate will be valid for {} days.", name, algorithm, signAlgorithm, keySize, validityInDays );
        // Generate public and private keys
        try
        {
            final KeyPair keyPair = generateKeyPair( algorithm.toUpperCase(), keySize );

            // Create X509 certificate with keys and specified domain
            final X509Certificate cert = CertificateManager.createX509V3Certificate( keyPair, validityInDays, name, name, name, signAlgorithm, sanDnsNames );

            // Store new certificate and private key in the key store
            store.setKeyEntry( alias, keyPair.getPrivate(), configuration.getPassword(), new X509Certificate[]{cert} );

            // Persist the changes in the store to disk.
            persist();
        }
        catch ( CertificateStoreConfigException | IOException | GeneralSecurityException ex )
        {
            reload(); // reset state of the store.
            throw new CertificateStoreConfigException( "Unable to generate new self-signed " + algorithm + " certificate.", ex );
        }

        // TODO Notify listeners that a new certificate has been created
    }

    /**
     * Returns a new public &amp; private key with the specified algorithm (e.g. DSA, RSA, etc.).
     *
     * @param algorithm DSA, RSA, etc.
     * @param keySize the desired key size. This is an algorithm-specific metric, such as modulus length, specified in number of bits.
     * @return a new public &amp; private key with the specified algorithm (e.g. DSA, RSA, etc.).
     * @throws GeneralSecurityException if the supplied algorithm does not have a key-pair generator
     */
    protected static synchronized KeyPair generateKeyPair( String algorithm, int keySize ) throws GeneralSecurityException
    {
        final KeyPairGenerator generator;
        if ( PROVIDER == null )
        {
            generator = KeyPairGenerator.getInstance( algorithm );
        }
        else
        {
            generator = KeyPairGenerator.getInstance( algorithm, PROVIDER );
        }
        generator.initialize( keySize, new SecureRandom() );
        return generator.generateKeyPair();
    }

    /**
     * Verifies that the subject of the certificate matches the domain of this XMPP service.
     *
     * @param certificate The certificate to verify (cannot be null)
     * @return true when the certificate subject is this domain, otherwise false.
     */
    public static boolean isForThisDomain( X509Certificate certificate )
    {
        final String domainName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

        final List<String> serverIdentities = CertificateManager.getServerIdentities( certificate );
        for ( String identity : serverIdentities )
        {
            if ( DNSUtil.isNameCoveredByPattern( domainName, identity ) )
            {
                return true;
            }
        }

        Log.info( "The supplied certificate chain does not cover the domain of this XMPP service ('" + domainName + "'). Instead, it covers " + Arrays.toString( serverIdentities.toArray( new String[ serverIdentities.size() ] ) ) );
        return false;
    }

    /**
     * Generates an alias that is currently unused in this store.
     *
     * @return An alias (never null).
     * @throws CertificateStoreConfigException if a unique alias could not be generated
     */
    protected synchronized String generateUniqueAlias() throws CertificateStoreConfigException
    {
        final String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        int index = 1;
        String alias = domain + "_" + index;
        try
        {
            while ( store.containsAlias( alias ) )
            {
                index = index + 1;
                alias = domain + "_" + index;
            }
            return alias;
        }
        catch ( KeyStoreException e )
        {
            throw new CertificateStoreConfigException( "Unable to generate a unique alias for this identity store.", e );
        }
    }

    /**
     * Removes all entries that reflect the local domain.
     *
     * This method iterates over all entries, and removes those that match the domain of this server.
     *
     * Note that the changes are not persisted by this method (as it is expected to be used in tandem with an insert.
     * @throws KeyStoreException if the key store could not be updated
     */
    protected synchronized void removeAllDomainEntries() throws KeyStoreException
    {
        final String domainName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

        final Set<String> toDelete = new HashSet<>();
        for ( final String alias : Collections.list( store.aliases() ) )
        {
            final Certificate certificate = store.getCertificate( alias );
            if ( !( certificate instanceof X509Certificate ) )
            {
                continue;
            }

            for ( String identity : CertificateManager.getServerIdentities( (X509Certificate) certificate ) )
            {
                if ( DNSUtil.isNameCoveredByPattern( domainName, identity ) )
                {
                    toDelete.add( alias );
                    break;
                }
            }
        }

        for ( final String alias : toDelete )
        {
            store.deleteEntry( alias );
        }
    }
}
