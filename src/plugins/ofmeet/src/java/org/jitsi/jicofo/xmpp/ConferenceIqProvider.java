/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.xmpp;


import org.jitsi.util.*;

import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.*;

import org.xmlpull.v1.*;

/**
 * Provider handles parsing of {@link org.jitsi.jicofo.xmpp.ConferenceIq} stanzas
 * and converting objects back to their XML representation.
 *
 * @author Pawel Domas
 */
public class ConferenceIqProvider
    implements IQProvider
{

    /**
     * Creates new instance of <tt>ConferenceIqProvider</tt>.
     */
    public ConferenceIqProvider()
    {
        ProviderManager providerManager = ProviderManager.getInstance();

        // <conference>
        providerManager.addIQProvider(
            ConferenceIq.ELEMENT_NAME,
            ConferenceIq.NAMESPACE,
            this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IQ parseIQ(XmlPullParser parser)
        throws Exception
    {
        String namespace = parser.getNamespace();

        // Check the namespace
        if (!ConferenceIq.NAMESPACE.equals(namespace))
        {
            return null;
        }

        String rootElement = parser.getName();

        ConferenceIq iq;

        if (ConferenceIq.ELEMENT_NAME.equals(rootElement))
        {
            iq = new ConferenceIq();
            String room
                = parser.getAttributeValue("", ConferenceIq.ROOM_ATTR_NAME);

            iq.setRoom(room);

            String ready
                = parser.getAttributeValue("", ConferenceIq.READY_ATTR_NAME);
            if (!StringUtils.isNullOrEmpty(ready))
            {
                iq.setReady(Boolean.valueOf(ready));
            }
            String focusJid
                = parser.getAttributeValue(
                        "", ConferenceIq.FOCUS_JID_ATTR_NAME);
            if (!StringUtils.isNullOrEmpty(focusJid))
            {
                iq.setFocusJid(focusJid);
            }
        }
        else
        {
            return null;
        }

        ConferenceIq.Property property = null;

        boolean done = false;

        while (!done)
        {
            switch (parser.next())
            {
                case XmlPullParser.END_TAG:
                {
                    String name = parser.getName();

                    if (rootElement.equals(name))
                    {
                        done = true;
                    }
                    else if (ConferenceIq.Property.ELEMENT_NAME.equals(name))
                    {
                        if (property != null)
                        {
                            iq.addProperty(property);
                            property = null;
                        }
                    }
                    break;
                }

                case XmlPullParser.START_TAG:
                {
                    String name = parser.getName();

                    if (ConferenceIq.Property.ELEMENT_NAME.equals(name))
                    {
                        property = new ConferenceIq.Property();

                        // Name
                        String propName
                            = parser.getAttributeValue(
                                    "",
                                    ConferenceIq.Property.NAME_ATTR_NAME);
                        if (!StringUtils.isNullOrEmpty(propName))
                        {
                            property.setName(propName);
                        }

                        // Value
                        String propValue
                            = parser.getAttributeValue(
                                    "",
                                    ConferenceIq.Property.VALUE_ATTR_NAME);
                        if (!StringUtils.isNullOrEmpty(propValue))
                        {
                            property.setValue(propValue);
                        }
                    }
                }
            }
        }

        return iq;
    }
}
