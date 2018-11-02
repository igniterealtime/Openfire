package org.xmpp.jnodes.smack;

import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;

import java.util.IllegalFormatException;

public class JingleNodesProvider implements IQProvider {

    public JingleChannelIQ parseIQ(final XmlPullParser parser) throws Exception {

        JingleChannelIQ iq = null;

        boolean done = false;
        int eventType;
        String elementName;
        String namespace;

        while (!done) {

            eventType = parser.getEventType();
            elementName = parser.getName();
            namespace = parser.getNamespace();

            if (eventType == XmlPullParser.START_TAG) {
                if (elementName.equals(JingleChannelIQ.NAME) && namespace.equals(JingleChannelIQ.NAMESPACE)) {

                    final String protocol = parser.getAttributeValue(null, "protocol");
                    final String porta = parser.getAttributeValue(null, "localport");
                    final String portb = parser.getAttributeValue(null, "remoteport");
                    final String host = parser.getAttributeValue(null, "host");

                    try {
                        iq = new JingleChannelIQ();
                        iq.setProtocol(protocol == null ? JingleChannelIQ.UDP : protocol);
                        if (host != null)
                            iq.setHost(host);
                        if (porta != null)
                            iq.setLocalport(Integer.valueOf(porta));
                        if (portb != null)
                            iq.setRemoteport(Integer.valueOf(portb));
                    } catch (final IllegalFormatException e) {
                        e.printStackTrace();
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }

                }
            } else if (eventType == XmlPullParser.END_TAG) {
                done = true;
            }
            if (!done)
                parser.next();
        }

        return iq;
    }
}
