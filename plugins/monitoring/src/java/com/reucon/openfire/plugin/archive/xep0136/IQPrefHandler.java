package com.reucon.openfire.plugin.archive.xep0136;

import org.dom4j.Element;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;

import com.reucon.openfire.plugin.archive.xep.AbstractIQHandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Message Archiving Preferences Handler.
 */
public class IQPrefHandler extends AbstractIQHandler implements ServerFeaturesProvider
{
    private static final Logger Log = LoggerFactory.getLogger( IQPrefHandler.class );
    private static final String NAMESPACE = "urn:xmpp:archive";
    private static final String NAMESPACE_PREF = "urn:xmpp:archive:pref";

    public IQPrefHandler()
    {
        super("Message Archiving Preferences Handler", "pref", NAMESPACE);
    }

    @SuppressWarnings("unchecked")
    public IQ handleIQ(IQ packet) throws UnauthorizedException
    {
        IQ reply = IQ.createResultIQ(packet);
        Element prefRequest = packet.getChildElement();

        Log.debug("Received pref request from {}", packet.getFrom());

        if (prefRequest.element("default") != null)
        {
            Element defaultItem = prefRequest.element("default");

            // User requests to set default modes
            defaultItem.attribute("save"); // body, false, message, stream
            defaultItem.attribute("otr");
            defaultItem.attribute("expire");
        }

        for (Element item : (List<Element>) prefRequest.elements("item"))
        {
            // User requests to set modes for a contact
            item.attribute("jid");
            item.attribute("save"); // body, false, message, stream
            item.attribute("otr");
            item.attribute("expire");
        }

        for (Element method : (List<Element>) prefRequest.elements("method"))
        {
            // User requests to set archiving method preferences
            method.attribute("type");
            method.attribute("use");
        }

        return reply;
    }

    public Iterator<String> getFeatures()
    {
        ArrayList<String> features = new ArrayList<String>();
        features.add(NAMESPACE_PREF);
        return features.iterator();
    }
}
