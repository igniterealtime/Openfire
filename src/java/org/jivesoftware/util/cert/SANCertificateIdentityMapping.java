package org.jivesoftware.util.cert;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
 * Certificate identity mapping that uses XMPP-OtherName SubjectAlternativeName
 * as the identity credentials
 * 
 * @author Victor Hong
 *
 */
public class SANCertificateIdentityMapping implements CertificateIdentityMapping {
	
	private static final Logger Log = LoggerFactory.getLogger(SANCertificateIdentityMapping.class);

    private static final String OTHERNAME_XMPP_OID = "1.3.6.1.5.5.7.8.5";
    
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
		List<String> identities = new ArrayList<String>();
        try {
            Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
            // Check that the certificate includes the SubjectAltName extension
            if (altNames == null) {
                return Collections.emptyList();
            }
            // Use the type OtherName to search for the certified server name
            for (List<?> item : altNames) {
                Integer type = (Integer) item.get(0);
                if (type == 0) {
                    // Type OtherName found so return the associated value
                    try {
                        // Value is encoded using ASN.1 so decode it to get the server's identity
                        ASN1InputStream decoder = new ASN1InputStream((byte[]) item.get(1));
                        Object object = decoder.readObject();
                        ASN1Sequence otherNameSeq = null;
                        if (object != null && object instanceof ASN1Sequence) {
                        	otherNameSeq = (ASN1Sequence) object;
                        } else {
                        	continue;
                        }
                        // Check the object identifier
                        ASN1ObjectIdentifier objectId = (ASN1ObjectIdentifier) otherNameSeq.getObjectAt(0);
                    	Log.debug("Parsing otherName for subject alternative names: " + objectId.toString() );

                        if ( !OTHERNAME_XMPP_OID.equals(objectId.getId())) {
                            // Not a XMPP otherName
                            Log.debug("Ignoring non-XMPP otherName, " + objectId.getId());
                            continue;
                        }

                        // Get identity string
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
	                            identities.add(identity);
	                        }
	                        decoder.close();
                        } catch (IllegalArgumentException ex) {
                        	// OF-517: othername formats are extensible. If we don't recognize the format, skip it.
                        	Log.debug("Cannot parse altName, likely because of unknown record format.", ex);
                        }
                    }
                    catch (UnsupportedEncodingException e) {
                        // Ignore
                    }
                    catch (IOException e) {
                        // Ignore
                    }
                    catch (Exception e) {
                        Log.error("Error decoding subjectAltName", e);
                    }
                }
                // Other types are not applicable for XMPP, so silently ignore them
            }
        }
        catch (CertificateParsingException e) {
            Log.error("Error parsing SubjectAltName in certificate: " + certificate.getSubjectDN(), e);
        }
        return identities;
	}

	/**
	 * Returns the short name of mapping
	 * 
	 * @return The short name of the mapping
	 */
	@Override
	public String name() {
		return "Subject Alternative Name Mapping";
	}

}
