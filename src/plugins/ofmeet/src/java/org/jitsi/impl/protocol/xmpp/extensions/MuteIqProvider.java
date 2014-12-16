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
 * The parser of {@link MuteIq}.
 *
 * @author Pawel Domas
 */
public class MuteIqProvider
    implements IQProvider
{
    /**
     * Registers this IQ provider into given <tt>ProviderManager</tt>.
     * @param providerManager the <tt>ProviderManager</tt> to which this
     *                        instance wil be bound to.
     */
    public void registerMuteIqProvider(ProviderManager providerManager)
    {
        providerManager.addIQProvider(
            MuteIq.ELEMENT_NAME,
            MuteIq.NAMESPACE,
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
        if (!MuteIq.NAMESPACE.equals(namespace))
        {
            return null;
        }

        String rootElement = parser.getName();

        MuteIq iq;

        if (MuteIq.ELEMENT_NAME.equals(rootElement))
        {
            iq = new MuteIq();

            String jid
                = parser.getAttributeValue("", MuteIq.JID_ATTR_NAME);

            iq.setJid(jid);
        }
        else
        {
            return null;
        }

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
                    break;
                }

                case XmlPullParser.TEXT:
                {
                    Boolean mute = Boolean.parseBoolean(parser.getText());
                    iq.setMute(mute);
                    break;
                }
            }
        }

        return iq;
    }
}
