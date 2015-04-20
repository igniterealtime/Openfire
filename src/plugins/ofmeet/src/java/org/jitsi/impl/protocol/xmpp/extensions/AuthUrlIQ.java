/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp.extensions;

import org.jitsi.util.*;
import org.jivesoftware.smack.packet.*;

/**
 * IQ sent to the focus in order to get the URL used for authentication with
 * external system.
 *
 * @author Pawel Domas
 */
public class AuthUrlIQ
    extends IQ
{
    public static final String NAMESPACE = ConferenceIq.NAMESPACE;

    public static final String ELEMENT_NAME = "auth-url";

    /**
     * The name of the attribute that holds authentication URL value.
     */
    public static final String URL_ATTRIBUTE_NAME = "url";

    /**
     * The name of the attribute that carries the name of conference room
     * which will be used as authentication context.
     */
    public static final String ROOM_NAME_ATTR_NAME = "room";

    /**
     * The URL used for authentication with external system.
     */
    private String url;

    /**
     * The conference room name used as a context for authentication.
     * muc_room_name@muc.server.name
     */
    private String room;

    @Override
    public String getChildElementXML()
    {
        StringBuilder xml = new StringBuilder();

        xml.append('<').append(ELEMENT_NAME);
        xml.append(" xmlns='").append(NAMESPACE).append("' ");

        printAttributes(xml);

        xml.append("/>");

        return xml.toString();
    }

    /**
     * Prints attributes in XML format to given <tt>StringBuilder</tt>.
     * @param out the <tt>StringBuilder</tt> instance used to construct XML
     *            representation of this element.
     */
    void printAttributes(StringBuilder out)
    {
        if (!StringUtils.isNullOrEmpty(url))
        {
            out.append(URL_ATTRIBUTE_NAME)
                    .append("='").append(url).append("' ");
        }
        if (!StringUtils.isNullOrEmpty(room))
        {
            out.append(ROOM_NAME_ATTR_NAME)
                    .append("='").append(room).append("' ");
        }
    }

    /**
     * Returns the value of {@link #URL_ATTRIBUTE_NAME} attribute.
     */
    public String getUrl()
    {
        return url;
    }

    /**
     * Sets the value of {@link #URL_ATTRIBUTE_NAME} attribute.
     * @param url authentication URL value to be set on this IQ instance.
     */
    public void setUrl(String url)
    {
        this.url = url;
    }

    /**
     * Returns the value of {@link #ROOM_NAME_ATTR_NAME} attribute.
     */
    public String getRoom()
    {
        return room;
    }

    /**
     * Sets the value of {@link #ROOM_NAME_ATTR_NAME} attribute.
     * @param room the name of MUC room to be set on this instance.
     */
    public void setRoom(String room)
    {
        this.room = room;
    }
}
