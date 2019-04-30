package org.jivesoftware.openfire.keystore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.security.cert.*;
import java.util.*;

/**
 * Utility methods for working with {@link javax.security.cert.Certificate} instances.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CertificateUtils
{
    private static final Logger Log = LoggerFactory.getLogger( CertificateUtils.class );

    /**
     * Returns all valid certificates from the provided input, where validity references the notBefore and notAfter
     * dates of each certificate.
     *
     * This method returns all certificates from the input for which {@link X509Certificate#checkValidity()} returns
     * true.
     *
     * The return value of this method is a Set, which means that duplicate certificates in the input are implicitly
     * being removed from the result.
     *
     * @param certificates An array of certificates (possibly empty, possibly null).
     * @return A Set of valid certificates (possibly empty, but never null).
     */
    public static Set<X509Certificate> filterValid( X509Certificate... certificates )
    {
        final Set<X509Certificate> results = new HashSet<>();

        if (certificates != null)
        {
            for ( X509Certificate certificate : certificates )
            {
                if ( certificate == null )
                {
                    continue;
                }

                try
                {
                    certificate.checkValidity();
                }
                catch ( CertificateExpiredException | CertificateNotYetValidException e )
                {
                    // Not yet or no longer valid. Don't include in result.
                    continue;
                }

                results.add( certificate );
            }
        }

        return results;
    }

    /**
     * Returns all valid certificates from the provided input, where validity references the notBefore and notAfter
     * dates of each certificate.
     *
     * This method returns all certificates from the input for which {@link X509Certificate#checkValidity()} returns
     * true.
     *
     * The return value of this method is a Set, which means that duplicate certificates in the input are implicitly
     * being removed from the result.
     *
     * @param certificates A Collection of certificates (possibly empty, possibly null).
     * @return A Set of valid certificates (possibly empty, but never null).
     */
    public static Set<X509Certificate> filterValid( Collection<X509Certificate> certificates )
    {
        if ( certificates == null )
        {
            return Collections.emptySet();
        }
        return filterValid( certificates.toArray( new X509Certificate[ certificates.size() ] ) );
    }

    /**
     * Transforms an array of certificates into TrustAnchor instances.
     *
     * This method does not set the nameConstraints parameter of the generated TrustAnchors.
     *
     * The return value of this method is a Set, which means that duplicate certificates in the input are implicitly
     * being removed from the result.
     *
     * @param certificates An array of certificates (possibly empty, possibly null).
     * @return A Set of valid certificates (possibly empty, but never null).
     */
    public static Set<TrustAnchor> toTrustAnchors( X509Certificate... certificates )
    {
        final Set<TrustAnchor> result = new HashSet<>();
        for ( X509Certificate certificate : certificates )
        {
            if ( certificate == null)
            {
                continue;
            }
            result.add( new TrustAnchor( certificate, null ) );
        }

        return result;
    }

    /**
     * Transforms a collection of certificates into TrustAnchor instances.
     *
     * This method does not set the nameConstraints parameter of the generated TrustAnchors.
     *
     * The return value of this method is a Set, which means that duplicate certificates in the input are implicitly
     * being removed from the result.
     *
     * @param certificates An array of certificates (possibly empty, possibly null).
     * @return A Set of valid certificates (possibly empty, but never null).
     */
    public static Set<TrustAnchor> toTrustAnchors( Collection<X509Certificate> certificates )
    {
        if ( certificates == null )
        {
            return Collections.emptySet();
        }

        return toTrustAnchors( certificates.toArray( new X509Certificate[ certificates.size() ] ) );
    }

    /**
     * Orders certificates, starting from the entity to be validated and progressing back toward the CA root.
     *
     * This implementation matches "issuers" to "subjects" of certificates in such a way that "issuer" value of a
     * certificate matches the "subject" value of the next certificate.
     *
     * When certificates are provided that do not belong to the same chain, a CertificateException is thrown.
     *
     * @param certificates an unordered collection of certificates (cannot be null).
     * @return An ordered list of certificates (possibly empty, but never null).
     * @throws CertificateException if there were multiple CA root certs
     */
    public static List<X509Certificate> order( Collection<X509Certificate> certificates ) throws CertificateException
    {
        final LinkedList<X509Certificate> orderedResult = new LinkedList<>();

        if ( certificates.isEmpty() ) {
            return orderedResult;
        }

        if (certificates.size() == 1) {
            orderedResult.addAll( certificates );
            return orderedResult;
        }

        final Map<Principal, X509Certificate> byIssuer = new HashMap<>();
        final Map<Principal, X509Certificate> bySubject = new HashMap<>();

        for ( final X509Certificate certificate : certificates ) {
            final Principal issuer = certificate.getIssuerDN();
            final Principal subject = certificate.getSubjectDN();

            // By issuer
            if ( issuer.equals( subject ))
            {
                // self-signed: use null key.
                final X509Certificate sameIssuer = byIssuer.put( null, certificate );
                if ( sameIssuer != null )
                {
                    throw new CertificateException( "The provided input should not contain multiple root CA certificates. Issuer of first detected Root CA certificate: " + issuer + " Issuer of second detected Root CA certificate: : " + sameIssuer );
                }
            }
            else
            {
                // regular issuer
                if ( byIssuer.put( issuer, certificate ) != null )
                {
                    throw new CertificateException( "The provided input should not contain multiple certificates with identical issuerDN values. Offending value: " + issuer );
                }
            }

            // By subject
            if ( bySubject.put( subject, certificate ) != null ) {
                throw new CertificateException( "The provided input should not contain multiple certificates with identical subjectDN values. Offending value: " + subject );
            }
        }

        // The first certificate will have a 'subject' value that's not an 'issuer' of any other chain.
        X509Certificate first = null;
        for ( Map.Entry<Principal, X509Certificate> entry : bySubject.entrySet() ) {
            final Principal subject = entry.getKey();
            final X509Certificate certificate = entry.getValue();

            if ( ! byIssuer.containsKey( subject ) ) {
                if (first == null) {
                    first = certificate;
                } else {
                    throw new CertificateException( "The provided input should not contain more than one certificates that has a subjectDN value that's not equal to the issuerDN value of another certificate." );
                }
            }
        }

        if (first == null) {
            throw new CertificateException( "The provided input should contain a certificate that has a subjectDN value that's not equal to the issuerDN value of any other certificate." );
        }

        orderedResult.add( first );

        // With the first certificate in hand, every following certificate should have a subject that's equal to the previous issuer value.
        X509Certificate next = bySubject.remove( first.getIssuerDN() );
        while (next != null) {
            orderedResult.add( next );
            next = bySubject.remove( next.getIssuerDN() );
        }

        // final check
        if (orderedResult.size() != certificates.size()) {
            throw new CertificateException( "Unable to recreate a certificate chain from the provided input." );
        }

        return orderedResult;
    }

    /**
     * Identifies the End Entity (or 'target') certificate in a chain. In an ordered chain, this is the certificate on
     * the opposite end of the CA / Root Certificate.
     *
     * This implementation can work with incomplete and unordered chains, as long as the provided certificates are all
     * part of the same chain (or chain segment). Each certificate in the chain is expected to have issued another
     * certificate from the chain, except for one. That one certificate is returned.
     *
     * When ordering the chain fails (for example, when the collection of certificates do not belong to one linear list)
     * the first certificate from the chain is returned.
     *
     * @param chain The chain (possibly incomplete or unordered, but not null, empty or malformed).
     * @return The end entity certificate (never null).
     * @throws CertificateException When no valid chain was provided.
     */
    public static X509Certificate identifyEndEntityCertificate( Collection<X509Certificate> chain ) throws CertificateException
    {
        if ( chain.isEmpty() )
        {
            throw new CertificateException();
        }

        try
        {
            return order( chain ).get( 0 );
        }
        catch ( CertificateException ex )
        {
            Log.warn( "Unable to order the provided chain. As a fallback, the end entity certificate is assumed to be the first certificate of the input.", ex );
            return chain.iterator().next();
        }
    }

    /**
     * Attempts to find a point in time on which each of the certificates in the chain will pass
     * {@link X509Certificate#checkValidity(Date)}
     *
     * @param chain The chain for which to find a valid point in time (cannot be null, or empty).
     * @return A date on which all certificates in the chain are valid, or null of no such date is available.
     */
    public static Date findValidPointInTime( X509Certificate... chain )
    {
        Date earliestNotAfter = null;
        Date latestNotBefore = null;

        for ( final X509Certificate certificate : chain )
        {
            if ( certificate == null ) continue; // ignore nulls.

            // Find the earliest 'notAfter'
            final Date notAfter = certificate.getNotAfter();
            if (earliestNotAfter == null || ( notAfter != null && notAfter.before( earliestNotAfter ) ) )
            {
                earliestNotAfter = notAfter;
            }

            // Find the latest 'notBefore'
            final Date notBefore = certificate.getNotBefore();
            if (latestNotBefore == null || ( notBefore != null && notBefore.after( latestNotBefore ) ) )
            {
                latestNotBefore = notBefore;
            }
        }

        if ( latestNotBefore != null && earliestNotAfter != null && latestNotBefore.before( earliestNotAfter ) )
        {
            return latestNotBefore;
        }
        else
        {
            // There's no single point in time in which all certificates in this chain are valid.
            return null;
        }
    }
}
