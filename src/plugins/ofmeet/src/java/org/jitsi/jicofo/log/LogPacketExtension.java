/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.log;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;

import java.util.*;

/**
 * Implements a <tt>PacketExtension</tt> that represents a XEP-0337 log message.
 *
 * @author Boris Grozev
 */
public class LogPacketExtension
    extends AbstractPacketExtension
{
    /**
     * The name of the "log" element.
     */
    public static final String LOG_ELEM_NAME = "log";

    /**
     * The namespace.
     */
    public static final String NAMESPACE = "urn:xmpp:eventlog";

    /**
     * The name of the "id" atttibute.
     */
    public static final String ID_ATTR_NAME = "id";

    /**
     * The name of the "message" element.
     */
    public static final String MESSAGE_ELEM_NAME = "message";

    /**
     * The name of the "tag" element.
     */
    public static final String TAG_ELEM_NAME = "tag";

    /**
     * Holds the text content of the "message" element.
     */
    private String message = null;

    /**
     * Holds the "tags".
     */
    private Map<String, String> tags = new HashMap<String,String>();

    /**
     * Initializes a new <tt>LogPacketExtension</tt>.
     */
    protected LogPacketExtension()
    {
        super(NAMESPACE, LOG_ELEM_NAME);
    }

    /**
     * Returns the ID of this <tt>LogPacketExtension</tt>
     *
     * @return the ID of this <tt>LogPacketExtension</tt>
     */
    public String getID()
    {
        return getAttributeAsString(ID_ATTR_NAME);
    }

    /**
     * Gets this <tt>LogPacketExtension</tt>'s "tags" as a map.
     *
     * @return this <tt>LogPacketExtension</tt>'s "tags" as a map.
     */
    public Map<String, String> getTags()
    {
        return tags;
    }

    /**
     * Gets the text content of the "message" child element of this
     * <tt>LogPacketExtension</tt>.
     * @return  the text content of the "message" child element of this
     * <tt>LogPacketExtension</tt>.
     */
    public String getMessage()
    {
        return message;
    }

    /**
     * Sets the text content of the "message" child element of this
     * <tt>LogPacketExtension</tt>.
     */
    public void setMessage(String message)
    {
        this.message = message;
    }

    /**
     * Adds a "tag" to this <tt>LogPacketExtension</tt>.
     * @param name the name of the tag.
     * @param value the value of the tag.
     */
    public void addTag(String name, String value)
    {
        tags.put(name, value);
    }

    /**
     * Gets the value of a tag with a given name.
     * @param name the name of the tag for which to get the value.
     * @return the value of the tag with value <tt>value</tt>.
     */
    public String getTagValue(String name)
    {
        return tags.get(name);
    }

    /**
     * {@inheritDoc}
     *
     * Note: not tested
     */
    @Override
    public String toXML()
    {
        StringBuilder builder = new StringBuilder();

        builder.append("<").append(LOG_ELEM_NAME).append(" ")
            .append("xmlns='").append(NAMESPACE).append("'");

        for(Map.Entry<String, Object> entry : attributes.entrySet())
        {
            builder.append(" ").append(entry.getKey()).append("='")
                .append(entry.getValue()).append("'");
        }

        builder.append("><").append(MESSAGE_ELEM_NAME).append(">")
            .append(message).append("</").append(MESSAGE_ELEM_NAME).append(">");

        for (Map.Entry<String, String> entry : tags.entrySet())
        {
            builder.append("<").append(TAG_ELEM_NAME)
                .append(" name='").append(entry.getKey())
                .append("' value='").append(entry.getValue())
                .append("'/>");
        }

        return builder.toString();
    }
}
