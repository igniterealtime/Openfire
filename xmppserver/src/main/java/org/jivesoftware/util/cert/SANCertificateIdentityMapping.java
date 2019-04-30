package org.jivesoftware.util.cert;

import org.bouncycastle.asn1.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Certificate identity mapping that uses SubjectAlternativeName as the identity credentials.
 * This implementation returns all subjectAltName entries that are a:
 * <ul>
 * <li>GeneralName of type otherName with the "id-on-xmppAddr" Object Identifier</li>
 * <li>GeneralName of type otherName with the "id-on-dnsSRV" Object Identifier</li>
 * <li>GeneralName of type DNSName</li>
 * <li>GeneralName of type UniformResourceIdentifier</li>
 * </ul>
 *
 * @author Victor Hong
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class SANCertificateIdentityMapping implements CertificateIdentityMapping
{

    private static final Logger Log = LoggerFactory.getLogger( SANCertificateIdentityMapping.class );

    /**
     * id-on-xmppAddr Object Identifier.
     *
     * @see <a href="http://tools.ietf.org/html/rfc6120">RFC 6120</a>
     */
    public static final String OTHERNAME_XMPP_OID = "1.3.6.1.5.5.7.8.5";

    /**
     * id-on-dnsSRV Object Identifier.
     *
     * @see <a href="https://tools.ietf.org/html/rfc4985">RFC 4985</a>
     */
    public static final String OTHERNAME_SRV_OID = "1.3.6.1.5.5.7.8.7";
    
    /**
     * User Principal Name (UPN) Object Identifier.
     *
     * @see <a href="http://www.oid-info.com/get/1.3.6.1.4.1.311.20.2.3">User Principal Name (UPN)</a>
     */
    public static final String OTHERNAME_UPN_OID = "1.3.6.1.4.1.311.20.2.3";

    
    /**
     * Returns the JID representation of an XMPP entity contained as a SubjectAltName extension
     * in the certificate. If none was found then return an empty list.
     *
     * @param certificate the certificate presented by the remote entity.
     * @return the JID representation of an XMPP entity contained as a SubjectAltName extension
     * in the certificate. If none was found then return an empty list.
     */
    @Override
    public List<String> mapIdentity( X509Certificate certificate )
    {
        List<String> identities = new ArrayList<>();
        try
        {
            Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
            // Check that the certificate includes the SubjectAltName extension
            if ( altNames == null )
            {
                return Collections.emptyList();
            }
            for ( List<?> item : altNames )
            {
                final Integer type = (Integer) item.get( 0 );
                final Object value = item.get( 1 ); // this is either a string, or a byte-array that represents the ASN.1 DER encoded form.
                final String result;
                switch ( type )
                {
                    case 0:
                        // OtherName: search for "id-on-xmppAddr" or 'sRVName' or 'userPrincipalName'
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

                if ( result != null )
                {
                    identities.add( result );
                }
            }
        }
        catch ( CertificateParsingException e )
        {
            Log.error( "Error parsing SubjectAltName in certificate: " + certificate.getSubjectDN(), e );
        }
        return identities;
    }

    /**
     * Returns the short name of mapping.
     *
     * @return The short name of the mapping (never null).
     */
    @Override
    public String name()
    {
        return "Subject Alternative Name Mapping";
    }

    /**
     * Parses the byte-array representation of a subjectAltName 'otherName' entry.
     * <p>
     * The provided 'OtherName' is expected to have this format:
     * <pre>{@code
     * OtherName ::= SEQUENCE {
     * type-id    OBJECT IDENTIFIER,
     * value      [0] EXPLICIT ANY DEFINED BY type-id }
     * }</pre>
     *
     * @param item A byte array representation of a subjectAltName 'otherName' entry (cannot be null).
     * @return an xmpp address, or null when the otherName entry does not relate to XMPP (or fails to parse).
     */
    public String parseOtherName( byte[] item )
    {
        if ( item == null || item.length == 0 )
        {
            return null;
        }

        try ( final ASN1InputStream decoder = new ASN1InputStream( item ) )
        {
            // By specification, OtherName instances must always be an ASN.1 Sequence.
            final ASN1Primitive object = decoder.readObject();
            final ASN1Sequence otherNameSeq = (ASN1Sequence) object;

            // By specification, an OtherName instance consists of:
            // - the type-id (which is an Object Identifier), followed by:
            // - a tagged value, of which the tag number is 0 (zero) and the value is defined by the type-id.
            final ASN1ObjectIdentifier typeId = (ASN1ObjectIdentifier) otherNameSeq.getObjectAt( 0 );
            final ASN1TaggedObject taggedValue = (ASN1TaggedObject) otherNameSeq.getObjectAt( 1 );

            final int tagNo = taggedValue.getTagNo();
            if ( tagNo != 0 )
            {
                throw new IllegalArgumentException( "subjectAltName 'otherName' sequence's second object is expected to be a tagged value of which the tag number is 0. The tag number that was detected: " + tagNo );
            }
            final ASN1Primitive value = taggedValue.getObject();

            switch ( typeId.getId() )
            {
                case OTHERNAME_SRV_OID:
                    return parseOtherNameDnsSrv( value );

                case OTHERNAME_XMPP_OID:
                    return parseOtherNameXmppAddr( value );
                    
                case OTHERNAME_UPN_OID:
                    return parseOtherNameUpn( value );

                default:
                    String otherName = parseOtherName(typeId, value);
                    if (otherName != null) {
                        return otherName;
                    }
                    Log.debug( "Ignoring subjectAltName 'otherName' type-id '{}' that's neither id-on-xmppAddr nor id-on-dnsSRV.", typeId.getId() );
                    return null;
            }
        }
        catch ( Exception e )
        {
            Log.warn( "Unable to parse a byte array (of length {}) as a subjectAltName 'otherName'. It is ignored.", item.length, e );
            return null;
        }
    }

    /**
     * Allow sub-class to support additional OID values, possibly taking typeId into account
     *
     * @param typeId The ASN.1 object identifier (cannot be null).
     * @param value The ASN.1 representation of the value (cannot be null).
     * @return The parsed otherName String value.
     */
    protected String parseOtherName(ASN1ObjectIdentifier typeId, ASN1Primitive value) {
        return null;
    }
    
    /**
     * Parses a SRVName value as specified by RFC 4985.
     *
     * This method parses the argument value as a DNS SRV Resource Record. Only when the parsed value refers to an XMPP
     * related service, the corresponding DNS domain name is returned (minus the service name).
     *
     * @param srvName The ASN.1 representation of the srvName value (cannot be null).
     * @return an XMPP address value, or null when the record does not relate to XMPP.
     */
    protected String parseOtherNameDnsSrv( ASN1Primitive srvName )
    {
        // RFC 4985 says that this should be a IA5 String. Lets be tolerant and allow all text-based values.
        final String value = ( (ASN1String) srvName ).getString();

        if ( value.toLowerCase().startsWith( "_xmpp-server." ) )
        {
            return value.substring( "_xmpp-server.".length() );
        }
        else if ( value.toLowerCase().startsWith( "_xmpp-client." ) )
        {
            return value.substring( "_xmpp-client.".length() );
        }
        else
        {
            // Not applicable to XMPP. Ignore.
            Log.debug( "srvName value '{}' of id-on-dnsSRV record is neither _xmpp-server nor _xmpp-client. It is being ignored.", value );
            return null;
        }
    }

    /**
     * Parse a XmppAddr value as specified in RFC 6120.
     *
     * @param xmppAddr The ASN.1 representation of the xmppAddr value (cannot be null).
     * @return The parsed xmppAddr value.
     */
    protected String parseOtherNameXmppAddr( ASN1Primitive xmppAddr )
    {
        // Get the nested object if the value is an ASN1TaggedObject or a sub-type of it
        if (ASN1TaggedObject.class.isAssignableFrom(xmppAddr.getClass())) {
            ASN1TaggedObject taggedObject = (ASN1TaggedObject) xmppAddr;
            ASN1Primitive objectPrimitive = taggedObject.getObject();
            if (ASN1String.class.isAssignableFrom(objectPrimitive.getClass())) {
                return ((ASN1String) objectPrimitive).getString();
            }
        }

        // RFC 6120 says that this should be a UTF8String. Lets be tolerant and allow all text-based values.
        return ( (ASN1String) xmppAddr ).getString();
    }
    
    /**
     * Parse a UPN value 
     *
     * @param value The ASN.1 representation of the UPN (cannot be null).
     * @return The parsed UPN value.
     */
    protected String parseOtherNameUpn( ASN1Primitive value )
    {
        String otherName = null;
        if (value instanceof ASN1TaggedObject) {
            ASN1TaggedObject taggedObject = (ASN1TaggedObject) value;
            ASN1Primitive objectPrimitive = taggedObject.getObject();
            if (objectPrimitive instanceof ASN1String) {
                otherName = ((ASN1String)objectPrimitive).getString();
            }
        }
        if (otherName == null) {
            Log.warn("UPN type unexpected, UPN extraction failed: " + value.getClass().getName() + ":" + value.toString());
        } else {
            Log.debug("UPN from certificate has value of: " + otherName );
        }
        return otherName;
    }    
}
