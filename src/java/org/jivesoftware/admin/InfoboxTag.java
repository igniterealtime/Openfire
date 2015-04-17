package org.jivesoftware.admin;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

public class InfoboxTag extends BodyTagSupport {

    private String type; // success, error, warning

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public int doEndTag() throws JspException {

        String body = "<div class=\"jive-"+type+"\">\n" +
        "    <table cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n" +
        "        <tbody>\n" +
        "        <tr><td class=\"jive-icon\"><img src=\"images/"+type+"-16x16.gif\" width=\"16\" height=\"16\" border=\"0\" alt=\"\"/></td>\n" +
        "            <td class=\"jive-icon-label\">\n" +
                        bodyContent.getString() +
        "            </td></tr>\n" +
        "        </tbody>\n" +
        "   </table>\n" +
        "</div><br>\n";

        try {
            pageContext.getOut().write( body );
        }
        catch (IOException ioe) {
            throw new JspException(ioe.getMessage());
        }

        return super.doEndTag();
    }
}
