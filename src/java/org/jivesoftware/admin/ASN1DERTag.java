package org.jivesoftware.admin;

import org.bouncycastle.asn1.*;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

/**
 * Creates a table that represents an ASN.1 encoded DER value.
 *
 * This tag creates a HTML table, that consists of one or two columns and an unspecified number of rows. Each cell
 * can contain a nested table (of similar format).
 */
public class ASN1DERTag extends BodyTagSupport {

    private byte[] value; // ASN.1 DER-encoded value

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    @Override
    public int doEndTag() throws JspException {

        try {
            final ASN1InputStream decoder = new ASN1InputStream(value);
            ASN1Primitive primitive = decoder.readObject();
            while (primitive != null && !(primitive instanceof ASN1Null)) {
                pageContext.getOut().write(doPrimitive(primitive));
                primitive = decoder.readObject();
            }
        } catch (Exception ex) {
            throw new JspException(ex.getMessage());
        }

        return super.doEndTag();
    }

    private String doPrimitive(ASN1Primitive primitive) throws IOException {
        if (primitive == null || primitive instanceof ASN1Null) {
            return "";
        } else if (primitive instanceof ASN1Sequence) {
            return doCollection(((ASN1Sequence) primitive).toArray());
        } else if (primitive instanceof ASN1Set) {
            return doCollection(((ASN1Set) primitive).toArray());
        } else if (primitive instanceof DERTaggedObject) {
            final DERTaggedObject tagged = ((DERTaggedObject) primitive);
            return "<table><tr><td>" + /* tagged.getTagNo() + */ "</td><td>" + doPrimitive(tagged.getObject()) + "</td></tr></table>";
        } else {
            return "<table><tr><td colspan='2'>" + asString(primitive) + "</td></tr></table>";
        }
    }

    private String doCollection(ASN1Encodable[] asn1Encodables) throws IOException {
        switch (asn1Encodables.length) {
            case 1:
                // one row, one column
                return "<table><tr><td colspan='2'>" + doPrimitive(asn1Encodables[0].toASN1Primitive()) + "</td></tr></table>";

            case 2:
                // one row, two columns
                return "<table><tr><td>" + doPrimitive(asn1Encodables[0].toASN1Primitive()) + "</td>"
                        + "<td>" + doPrimitive(asn1Encodables[1].toASN1Primitive()) + "</td></tr></table>";

            default:
                // a row per per item
                final StringBuilder sb = new StringBuilder();
                for (ASN1Encodable asn1Encodable : asn1Encodables) {
                    sb.append("<table><tr><td colspan='2'>").append(doPrimitive(asn1Encodable.toASN1Primitive())).append("</td></tr></table>");
                }
                return sb.toString();
        }
    }

    private String asString(ASN1Primitive primitive) {
        if (primitive == null || primitive instanceof ASN1Null) {
            return "";
        }
        if (primitive instanceof ASN1String) {
            return ((ASN1String) primitive).getString();
        }
        if (primitive instanceof DERUTCTime) {
            return ((DERUTCTime) primitive).getAdjustedTime();
        }
        if (primitive instanceof DERGeneralizedTime) {
            return ((DERGeneralizedTime) primitive).getTime();
        }
        if (primitive instanceof ASN1ObjectIdentifier) {
            switch (((ASN1ObjectIdentifier) primitive).getId()) {
                case "1.3.6.1.5.5.7.8.5":
                    return "xmppAddr";
                default:
                    return primitive.toString();
            }
        }
        return primitive.toString();
    }
}
