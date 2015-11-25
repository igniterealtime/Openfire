package org.jivesoftware.admin;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

public class ContentBoxTag extends BodyTagSupport {

    private String title;

    @Override
    public int doEndTag() throws JspException
    {
        final String body = "<div class=\"jive-contentBoxHeader\">" +
            title +
        "</div>\n" +
        "<div class=\"jive-contentBox\">" +
            bodyContent.getString() +
        "</div>\n";

        try {
            pageContext.getOut().write( body );
        }
        catch (IOException ioe) {
            throw new JspException(ioe.getMessage());
        }

        return super.doEndTag();
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle( String title )
    {
        this.title = title;
    }
}
