/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;
import org.jitsi.util.*;
import org.jivesoftware.smack.packet.*;

import java.util.*;

/**
 * FIXME: move to Jitsi ?
 *
 * The IQ used by Jitsi Meet conference participant to contact the focus and ask
 * it to handle the conference in some multi user chat room.
 *
 * @author Pawel Domas
 */
public class ConferenceIq
    extends IQ
{
    /**
     * Focus namespace.
     */
    public final static String NAMESPACE = "http://jitsi.org/protocol/focus";

    /**
     * XML element name for the <tt>ConferenceIq</tt>.
     */
    public static final String ELEMENT_NAME = "conference";

    /**
     * The name of the attribute that stores the name of multi user chat room
     * that is hosting Jitsi Meet conference.
     */
    public static final String ROOM_ATTR_NAME = "room";

    /**
     * The name of the attribute that indicates if the focus has already joined
     * the room(otherwise users might decide not to join yet).
     */
    public static final String READY_ATTR_NAME = "ready";

    /**
     * The name of the attribute that tells to the user what is
     * the jid of the focus user.
     */
    public static final String FOCUS_JID_ATTR_NAME = "focusjid";

    /**
     * MUC room name hosting Jitsi Meet conference.
     */
    private String room;

    /**
     * Indicates if the focus is already in the MUC room and conference is ready
     * to be joined.
     */
    private Boolean ready;

    /**
     * The JID of authenticated focus user.
     */
    private String focusJid;

    /**
     * The list of configuration properties that are contained in this IQ.
     */
    private List<Property> properties = new ArrayList<Property>();

    /**
     * Prints attributes in XML format to given <tt>StringBuilder</tt>.
     * @param out the <tt>StringBuilder</tt> instance used to construct XML
     *            representation of this element.
     */
    void printAttributes(StringBuilder out)
    {
        out.append(ROOM_ATTR_NAME)
            .append("=")
            .append("'").append(room).append("' ");

        if (ready != null)
        {
            out.append(READY_ATTR_NAME)
                .append("=")
                .append("'").append(ready).append("' ");
        }

        if (!StringUtils.isNullOrEmpty(focusJid))
        {
            out.append(FOCUS_JID_ATTR_NAME)
                .append("=")
                .append("'").append(focusJid).append("' ");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getChildElementXML()
    {
        StringBuilder xml = new StringBuilder();

        xml.append('<').append(ELEMENT_NAME);
        xml.append(" xmlns='").append(NAMESPACE).append("' ");

        printAttributes(xml);

        Collection<PacketExtension> extensions =  getExtensions();
        if (extensions.size() > 0 || properties.size() > 0)
        {
            xml.append(">");
            for (PacketExtension extension : extensions)
            {
                xml.append(extension.toXML());
            }
            for (Property property : properties)
            {
                xml.append(property.toXML());
            }
            xml.append("</").append(ELEMENT_NAME).append(">");
        }
        else
        {
            xml.append("/>");
        }

        return xml.toString();
    }

    /**
     * Returns the value of {@link #ready} attribute.
     */
    public Boolean isReady()
    {
        return ready;
    }

    /**
     * Sets the value of {@link #ready} attribute of this <tt>ConferenceIq</tt>.
     * @param ready the value to be set as {@link #ready} attribute value.
     */
    public void setReady(Boolean ready)
    {
        this.ready = ready;
    }

    /**
     * Returns the value of {@link #room} attribute of this
     * <tt>ConferenceIq</tt>.
     */
    public String getRoom()
    {
        return room;
    }

    /**
     * Sets the {@link #room} attribute of this <tt>ConferenceIq</tt>.
     * @param room the value to be set as {@link #room} attribute value.
     */
    public void setRoom(String room)
    {
        this.room = room;
    }

    /**
     * Returns the value of {@link #FOCUS_JID_ATTR_NAME} held by this IQ.
     */
    public String getFocusJid()
    {
        return focusJid;
    }

    /**
     * Sets the value for the focus JID attribute.
     * @param focusJid a string with the JID of focus user('username@domain').
     */
    public void setFocusJid(String focusJid)
    {
        this.focusJid = focusJid;
    }

    /**
     * Adds property packet extension to this IQ.
     * @param property the instance <tt>Property</tt> to be added to this IQ.
     */
    public void addProperty(Property property)
    {
        properties.add(property);
    }

    /**
     * Returns the list of properties contained in this IQ.
     * @return list of <tt>Property</tt> contained in this IQ.
     */
    public List<Property> getProperties()
    {
        return properties;
    }

    /**
     * Converts list of properties contained in this IQ into the name to value
     * mapping.
     * @return the map of property names to values as strings.
     */
    public Map<String, String> getPropertiesMap()
    {
        Map<String, String> properties= new HashMap<String, String>();
        for (Property property : this.properties)
        {
            properties.put(property.getName(), property.getValue());
        }
        return properties;
    }

    /**
     * Packet extension for configuration properties.
     */
    public static class Property extends AbstractPacketExtension
    {
        /**
         * The name of property XML element.
         */
        public static final String ELEMENT_NAME = "property";

        /**
         * The name of 'name' property attribute.
         */
        public static final String NAME_ATTR_NAME = "name";

        /**
         * The name of 'value' property attribute.
         */
        public static final String VALUE_ATTR_NAME = "value";

        /**
         * Creates new empty <tt>Property</tt> instance.
         */
        public Property()
        {
            super(null, ELEMENT_NAME);
        }

        /**
         * Creates new <tt>Property</tt> instance initialized with given
         * <tt>name</tt> and <tt>value</tt> values.
         *
         * @param name a string that will be the name of new property.
         * @param value a string value for new property.
         */
        public Property(String name, String value)
        {
            this();

            setName(name);
            setValue(value);
        }

        /**
         * Sets the name of this property.
         * @param name a string that will be the name of this property.
         */
        public void setName(String name)
        {
            setAttribute(NAME_ATTR_NAME, name);
        }

        /**
         * Returns the name of this property.
         */
        public String getName()
        {
            return getAttributeAsString(NAME_ATTR_NAME);
        }

        /**
         * Sets the value of this property.
         * @param value a string value for new property.
         */
        public void setValue(String value)
        {
            setAttribute(VALUE_ATTR_NAME, value);
        }

        /**
         * Returns the value of this property.
         */
        public String getValue()
        {
            return getAttributeAsString(VALUE_ATTR_NAME);
        }
    }
}
