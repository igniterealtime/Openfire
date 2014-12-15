/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.util.*;

import org.jitsi.jicofo.*;
import org.jitsi.protocol.xmpp.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smackx.pubsub.*;
import org.jivesoftware.smackx.pubsub.listener.*;

import java.util.*;

/**
 * XMPP Pub-sub node implementation of {@link OperationSetSubscription}.
 *
 * @author Pawel Domas
 */
public class OpSetSubscriptionImpl
    implements OperationSetSubscription,
               ItemEventListener
{
    /**
     * The logger used by this instance.
     */
    private final static Logger logger
        = Logger.getLogger(OpSetSubscriptionImpl.class);

    /**
     * The configuration property used to specify address for pub-sub node.
     */
    public static final String PUBSUB_ADDRESS_PNAME
        = "org.jitsi.focus.pubsub.ADDRESS";

    /**
     * Smack PubSub manager.
     */
    private PubSubManager manager;

    /**
     * PubSub address used.
     */
    private final String pubSubAddress;

    /**
     * Our JID used for PubSub registration.
     */
    private String ourJid;

    /**
     * The map of PubSub node listeners.
     */
    private Map<String, SubscriptionListener> listenerMap
        = new HashMap<String, SubscriptionListener>();

    /**
     * Parent XMPP provider used to hndle the protocol.
     */
    private final XmppProtocolProvider parentProvider;

    /**
     * Creates new instance of {@link OpSetSubscriptionImpl}.
     *
     * @param parentProvider the XMPP provider instance.
     */
    public OpSetSubscriptionImpl(XmppProtocolProvider parentProvider)
    {
        this.parentProvider = parentProvider;

        this.pubSubAddress
            = FocusBundleActivator.getConfigService()
                    .getString(PUBSUB_ADDRESS_PNAME);
    }

    /**
     * Lazy initializer for our JID field(it's not available before provider
     * gets connected).
     */
    private String getOurJid()
    {
        if (ourJid == null)
        {
            this.ourJid = parentProvider.getOurJid();
        }
        return ourJid;
    }

    /**
     * Lazy initializer for PubSub manager.
     */
    private PubSubManager getManager()
    {
        if (manager == null)
        {
            manager = new PubSubManager(
                parentProvider.getConnection(), pubSubAddress);
        }
        return manager;
    }

    /**
     * Checks if given <tt>jid</tt> is registered for PubSub updates on given
     * <tt>node</tt>.
     */
    private boolean isSubscribed(String jid, Node node)
        throws XMPPException
    {
        // FIXME: consider using local flag rather than getting the list
        // of subscriptions
        for (Subscription subscription : node.getSubscriptions())
        {
            if (subscription.getJid().equals(jid))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void subscribe(String node, SubscriptionListener listener)
    {
        listenerMap.put(node, listener);

        PubSubManager manager = getManager();
        try
        {
            Node pubSubNode = manager.getNode(node);
            if (!isSubscribed(getOurJid(), pubSubNode))
            {
                // FIXME: Is it possible that we will be subscribed after
                // our connection dies ? If yes we won't add listener here
                // and won't receive notifications
                pubSubNode.addItemEventListener(this);
                pubSubNode.subscribe(parentProvider.getOurJid());
            }
        }
        catch (XMPPException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unSubscribe(String node)
    {
        if (!listenerMap.containsKey(node))
        {
            logger.warn("No PUBSUB listener for " + node);
            return;
        }

        PubSubManager manager = getManager();

        try
        {
            Node pubSubNode = manager.getNode(node);
            if (isSubscribed(getOurJid(), pubSubNode))
            {
                pubSubNode.unsubscribe(getOurJid());
            }
        }
        catch (XMPPException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Fired by Smack on PubSub update.
     *
     * {@inheritDoc}
     */
    @Override
    public void handlePublishedItems(ItemPublishEvent event)
    {
        String nodeId = event.getNodeId();

        if (logger.isDebugEnabled())
            logger.debug("PubSub update for node: " + nodeId);

        SubscriptionListener listener = listenerMap.get(nodeId);
        if (listener != null)
        {
            for(Object item : event.getItems())
            {
                if(!(item instanceof PayloadItem))
                    continue;

                PayloadItem payloadItem = (PayloadItem) item;
                listener.onSubscriptionUpdate(nodeId, payloadItem.getPayload());
            }
        }
    }
}
