package org.jivesoftware.util.cert;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Certificate identity mapping that uses SubjectAlternativeName as the identity credentials.
 * This implementation returns all subjectAltName entries that are a:
 * <ul>
 *     <li>GeneralName of type otherName with the "id-on-xmppAddr" Object Identifier</li>
 *     <li>GeneralName of type otherName with the "id-on-dnsSRV" Object Identifier</li>
 *     <li>GeneralName of type DNSName</li>
 *     <li>GeneralName of type UniformResourceIdentifier</li>
 * </ul>
 *
 * @author Victor Hong
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class SANCertificateIdentityMapping implements CertificateIdentityMapping {
	
	private static final Logger Log = LoggerFactory.getLogger(SANCertificateIdentityMapping.class);

    /**
     * id-on-xmppAddr Object Identifier.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6120>RFC 6120</a>
     */
    public static final String OTHERNAME_XMPP_OID = "1.3.6.1.5.5.7.8.5";

    /**
     * id-on-dnsSRV Object Identifier.
     *
     * @see <a href="https://www.ietf.org/rfc/rfc4985.txt">RFC 4985</a>
     */
    public static final String OTHERNAME_SRV_OID = "1.3.6.1.5.5.7.8.7";

    /**
     * Returns the JID representation of an XMPP entity contained as a SubjectAltName extension
     * in the certificate. If none was found then return an empty list.
     *
     * @param certificate the certificate presented by the remote entity.
     * @return the JID representation of an XMPP entity contained as a SubjectAltName extension
     *         in the certificate. If none was found then return an empty list.
     */
	@Override
	public List<String> mapIdentity(X509Certificate certificate) {
		List<String> identities = new ArrayList<>();
        try {
            Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
            // Check that the certificate includes the SubjectAltName extension
            if (altNames == null) {
                return Collections.emptyList();
            }
            for (List<?> item : altNames) {
                final Integer type = (Integer) item.get(0);
                final Object value = item.get(1);
                final String result;
                switch ( type ) {
                    case 0:
                        // OtherName: search for "id-on-xmppAddr" or 'sRVName'
                        result = parseOtherName( (byte[]) value );
                        break;
                    case 2:
                        // DNS
                        result = (String) value;
                        break;
                    case 6:
                        // URI
                        result = (String) value;
                        break;
                    default:
                        // Not applicable to XMPP, so silently ignore them
                        result = null;
                        break;
                }

                if ( result != null ) {
                    identities.add( result );
                }
            }
        }
        catch (CertificateParsingException e) {
            Log.error("Error parsing SubjectAltName in certificate: " + certificate.getSubjectDN(), e);
        }
        return identities;
	}

	/**
	 * Returns the short name of mapping.
	 * 
	 * @return The short name of the mapping (never null).
	 */
	@Override
	public String name() {
		return "Subject Alternative Name Mapping";
	}

    /**
     * Parses the byte-array representation of a subjectAltName 'otherName' entry, returning the "id-on-xmppAddr" value
     * when that is in the entry.
     *
     * @param item A byte array representation of a subjectAltName 'otherName' entry (cannot be null).
     * @return an "id-on-xmppAddr" value (which is expected to be a JID), or null.
     */
    public static String parseOtherName( byte[] item ) {
        // Type OtherName found so return the associated value
        try (ASN1InputStream decoder = new ASN1InputStream(item)) {
            // Value is encoded using ASN.1 so decode it to get the server's identity
            Object object = decoder.readObject();
            ASN1Sequence otherNameSeq;
            if (object != null && object instanceof ASN1Sequence) {
                otherNameSeq = (ASN1Sequence) object;
            } else {
                return null;
            }
            // Check the object identifier
            ASN1ObjectIdentifier objectId = (ASN1ObjectIdentifier) otherNameSeq.getObjectAt(0);
            Log.debug("Parsing otherName for subject alternative names: " + objectId.toString() );

            // Get identity string
            if ( OTHERNAME_SRV_OID.equals(objectId.getId())) {
                return parseOtherNameDnsSrv( otherNameSeq );
            } else if ( OTHERNAME_XMPP_OID.equals(objectId.getId())) {
                // Not a XMPP otherName
                return parseOtherNameXmppAddr( otherNameSeq );
            } else
            {
                Log.debug("Ignoring otherName '{}' that's neither id-on-xmppAddr nor id-on-dnsSRV.", objectId.getId());
                return null;
            }
        }
        catch (Exception e) {
            Log.error("Error decoding subjectAltName", e);
        }
        return null;
    }

    public static String parseOtherNameDnsSrv( ASN1Sequence otherNameSeq )
    {
        Log.debug( "Parsing SRVName otherName..." );
        try {
            final ASN1Encodable o = otherNameSeq.getObjectAt( 1 );
            final DERUTF8String derStr = DERUTF8String.getInstance( o );
            final String value = derStr.getString();
            if ( value.startsWith( "_xmpp-server." )) {
                Log.debug( "Found _xmpp-server SRVName otherName" );
                return value.substring( "_xmpp-server.".length() );
            }
            if ( value.startsWith( "_xmpp-client." )) {
                Log.debug( "Found _xmpp-client SRVName otherName" );
                return value.substring( "_xmpp-client.".length() );
            }
            else
            {
                Log.debug( "SRVName otherName '{}' was neither _xmpp-server nor _xmpp-client. It is being ignored.", value );
                return null;
            }
        } catch (IllegalArgumentException ex) {
            Log.debug("Cannot parse id-on-dnsSRV otherName, likely because of unknown record format.", ex);
            return null;
        }
    }

    public static String parseOtherNameXmppAddr( ASN1Sequence otherNameSeq )
    {
        try {
            final String identity;
            ASN1Encodable o = otherNameSeq.getObjectAt(1);
            if (o instanceof DERTaggedObject) {
                ASN1TaggedObject ato = DERTaggedObject.getInstance(o);
                Log.debug("... processing DERTaggedObject: " + ato.toString());
                // TODO: there's bound to be a better way...
                identity = ato.toString().substring(ato.toString().lastIndexOf(']')+1).trim();
            } else {
                DERUTF8String derStr = DERUTF8String.getInstance(o);
                identity = derStr.getString();
            }
            if (identity != null && identity.length() > 0) {
                // Add the decoded server name to the list of identities
                return identity;
            }
        } catch (IllegalArgumentException ex) {
            // OF-517: othername formats are extensible. If we don't recognize the format, skip it.
            Log.debug("Cannot parse id-on-xmppAddr otherName, likely because of unknown record format.", ex);
        }
        return null;
    }
}
