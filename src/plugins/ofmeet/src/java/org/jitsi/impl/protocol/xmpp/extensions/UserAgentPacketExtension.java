/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp.extensions;

import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.*;
import org.xmlpull.v1.*;

/**
 * @author George Politis
 */
public class UserAgentPacketExtension
        implements PacketExtension
{
    /**
     * Name space of browser packet extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/jitmeet/user-agent";

    /**
     * XML element name of browser packet extension.
     */
    public static final String ELEMENT_NAME = "user-agent";

    /**
     * The browser name.
     */
    private String userAgent = null;

    /**
     * Ctor.
     *
     * @param userAgent
     */
    public UserAgentPacketExtension(String userAgent)
    {
        this.userAgent = userAgent;
    }

    /**
     * Gets the user agent.
     *
     * @return the user agent.
     */
    public String getUserAgent()
    {
        return this.userAgent;
    }

    /**
     * Sets the user agent.
     *
     * @param userAgent the user agent.
     */
    public void setUserAgent(String userAgent)
    {
        this.userAgent = userAgent;
    }

    public String getElementName()
    {
        return ELEMENT_NAME;
    }

    public String getNamespace()
    {
        return NAMESPACE;
    }

    public String toXML()
    {
        return new StringBuilder()
                .append("<").append(ELEMENT_NAME).append(" xmlns=\"")
                .append(NAMESPACE)
                .append("\">")
                .append(this.getUserAgent())
                .append("</")
                .append(ELEMENT_NAME)
                .append('>')
                .toString();
    }

    public static class Provider implements PacketExtensionProvider
    {
        public PacketExtension parseExtension(XmlPullParser parser)
                throws Exception
        {
            parser.next();
            String userAgent = parser.getText();

            while (parser.getEventType() != XmlPullParser.END_TAG)
            {
                parser.next();
            }

            return new UserAgentPacketExtension(userAgent);
        }
    }
}
