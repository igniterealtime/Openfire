/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.log;

import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.*;
import org.xmlpull.v1.XmlPullParser;

/**
 * Implements a <tt>PacketExtensionProvider</tt> for XEP-0337 log extensions.
 *
 * @author Boris Grozev
 */
public class LogExtensionProvider
    implements PacketExtensionProvider
{
    /**
     * {@inheritDoc}
     */
    @Override
    public PacketExtension parseExtension(XmlPullParser parser)
            throws Exception
    {
        LogPacketExtension result = new LogPacketExtension();

        for (int i = 0; i < parser.getAttributeCount(); i++)
        {
            result.setAttribute(
                    parser.getAttributeName(i),
                    parser.getAttributeValue(i));
        }

        boolean done = false;
        int eventType;
        String elementName;

        while (!done)
        {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.START_TAG)
            {
                if (LogPacketExtension.MESSAGE_ELEM_NAME.equals(elementName))
                {
                    result.setMessage(parser.nextText());
                }
                else if(LogPacketExtension.TAG_ELEM_NAME.equals(elementName))
                {
                    String nameAttr = null;
                    String valueAttr = null;

                    for (int i = 0; i < parser.getAttributeCount(); i++)
                    {
                        String attrName = parser.getAttributeName(i);
                        if ("name".equals(attrName))
                            nameAttr = parser.getAttributeValue(i);
                        else if ("value".equals(attrName))
                            valueAttr = parser.getAttributeValue(i);
                    }

                    if (nameAttr != null && valueAttr != null)
                    {
                        result.addTag(nameAttr, valueAttr);
                    }
                }
            }
            else if (eventType == XmlPullParser.END_TAG)
            {
                if (LogPacketExtension.LOG_ELEM_NAME.equals(elementName))
                    done = true;
            }
        }
        return result;

    }
}

