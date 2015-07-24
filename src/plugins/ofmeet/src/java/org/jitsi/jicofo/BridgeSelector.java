/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.util.Logger;

import org.jitsi.protocol.xmpp.*;
import org.jitsi.util.*;
import org.jitsi.videobridge.stats.*;

import org.jivesoftware.smack.packet.*;

import java.util.*;

/**
 * Class exposes methods for selecting best videobridge from all currently
 * available. Videobridge state is tracked through PubSub notifications and
 * based on feedback from Jitsi Meet conference focus.
 *
 * @author Pawel Domas
 */
public class BridgeSelector
    implements SubscriptionListener
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(BridgeSelector.class);

    /**
     * Property used to configure mapping of videobridge JIDs to PubSub nodes.
     * Single mapping is defined by writing videobridge JID followed by ':' and
     * pub-sub node name. If multiple mapping are to be appended then ';' must
     * be used to separate each mapping.
     *
     * org.jitsi.focus.BRIDGE_PUBSUB_MAPPING
     * =jvb1.server.net:pubsub1;jvb2.server.net:pubsub2;jvb3.server.net:pubsub3
     *
     * PubSub service node is discovered automatically for now and the first one
     * that offer PubSub feature is selected. Then this selector class
     * subscribes for all mapped PubSub nodes on that service for notifications.
     *
     * FIXME: we do not unsubscribe from pubsub notifications on shutdown
     */
    public static final String BRIDGE_TO_PUBSUB_PNAME
        = "org.jitsi.focus.BRIDGE_PUBSUB_MAPPING";

    /**
     * Operation set used to subscribe to PubSub nodes notifications.
     */
    private final OperationSetSubscription subscriptionOpSet;

    /**
     * The map of bridge JID to <tt>BridgeState</tt>.
     */
    private Map<String, BridgeState> bridges
        = new HashMap<String, BridgeState>();

    /**
     * Pre-configured JVB used as last chance option even if no bridge has been
     * auto-detected on startup.
     */
    private String preConfiguredBridge;

    /**
     * The map of Pub-Sub nodes to videobridge JIDs.
     */
    private Map<String, String> pubSubToBridge = new HashMap<String, String>();

    /**
     * Creates new instance of {@link BridgeSelector}.
     *
     * @param subscriptionOpSet the operations set that will be used by this
     *                          instance to subscribe to pub-sub notifications.
     */
    public BridgeSelector(OperationSetSubscription subscriptionOpSet)
    {
        this.subscriptionOpSet = subscriptionOpSet;

        String mappingPropertyValue
            = FocusBundleActivator.getConfigService()
                .getString(BRIDGE_TO_PUBSUB_PNAME);

        if (StringUtils.isNullOrEmpty(mappingPropertyValue))
        {
            return;
        }

        String[] pairs = mappingPropertyValue.split(";");
        for (String pair : pairs)
        {
            String[] bridgeAndNode = pair.split(":");
            String bridge = bridgeAndNode[0];
            String pubSubNode = bridgeAndNode[1];
            pubSubToBridge.put(pubSubNode, bridge);

            logger.info("Pub-sub mapping: " + pubSubNode + " -> " + bridge);
        }
    }

    /**
     * Adds next Jitsi Videobridge XMPP address to be observed by this selected
     * and taken into account in best bridge selection process.
     *
     * @param bridgeJid the JID of videobridge to be added to this selector's
     *                  set of videobridges.
     */
    public void addJvbAddress(String bridgeJid)
    {
        String pubSubNode = findNodeForBridge(bridgeJid);
        if (pubSubNode != null)
        {
            logger.info(
                "Subscribing to pubsub notfications to "
                    + pubSubNode + " for " + bridgeJid);
            subscriptionOpSet.subscribe(pubSubNode, this);
        }
        else
        {
            logger.warn("No pub-sub node mapped for " + bridgeJid
                        + " statistics will not be tracked for this instance.");
        }

        bridges.put(bridgeJid, new BridgeState(bridgeJid));
    }

    /**
     * Returns least loaded and *operational* videobridge. By operational it
     * means that it was not reported by any of conference focuses to fail while
     * allocating channels.
     *
     * @return the JID of least loaded videobridge or <tt>null</tt> if there are
     *         not any operational bridges currently.
     */
    public String selectVideobridge()
    {
        // FIXME: Consider caching elected bridge and reset on stats
        // or is operational change
        if (bridges.size() == 0)
        {
            // No bridges registered
            return null;
        }

        // Elect best bridge
        Iterator<BridgeState> bridgesIter = bridges.values().iterator();

        BridgeState bestChoice = bridgesIter.next();
        while (bridgesIter.hasNext())
        {
            BridgeState candidate = bridgesIter.next();
            if (candidate.compareTo(bestChoice) < 0)
            {
                bestChoice = candidate;
            }
        }

        return bestChoice.isOperational ? bestChoice.jid : null;
    }

    /**
     * Returns the list of all known videobridges JIDs ordered by load and
     * *operational* status. Not operational bridges are at the end of the list.
     */
    public List<String> getPrioritizedBridgesList()
    {
        ArrayList<BridgeState> bridgeList
            = new ArrayList<BridgeState>(bridges.values());

        Collections.sort(bridgeList);

        boolean isAnyBridgeUp = false;
        ArrayList<String> bridgeJidList = new ArrayList<String>();
        for (BridgeState bridgeState : bridgeList)
        {
            bridgeJidList.add(bridgeState.jid);
            if (bridgeState.isOperational)
            {
                isAnyBridgeUp = true;
            }
        }
        // Check if we have pre-configured bridge to include in the list
        if (!StringUtils.isNullOrEmpty(preConfiguredBridge)
            && !bridgeJidList.contains(preConfiguredBridge))
        {
            // If no auto-detected bridge is up then put pre-configured up front
            if (!isAnyBridgeUp)
            {
                bridgeJidList.add(0, preConfiguredBridge);
            }
            else
            {
                bridgeJidList.add(preConfiguredBridge);
            }
        }
        return bridgeJidList;
    }

    /**
     * Updates given *operational* status of the videobridge identified by given
     * <tt>bridgeJid</tt> address.
     *
     * @param bridgeJid the XMPP address of the bridge.
     * @param isWorking <tt>true</tt> if bridge successfully allocated
     *                  the channels which means it is in *operational* state.
     */
    public void updateBridgeOperationalStatus(String bridgeJid,
                                              boolean isWorking)
    {
        BridgeState bridge = bridges.get(bridgeJid);
        if (bridge != null)
        {
            bridge.setIsOperational(isWorking);
        }
        else
        {
            logger.warn("No bridge registered for jid: " + bridgeJid);
        }
    }

    /**
     * Returns videobridge JID for given pub-sub node, but only if it has been
     * added using {@link #addJvbAddress(String)} method.
     *
     * @param pubSubNode the pub-sub node name.
     *
     * @return videobridge JID for given pub-sub node.
     */
    public String getBridgeForPubSubNode(String pubSubNode)
    {
        BridgeState bridge = findBridgeForNode(pubSubNode);
        return bridge != null ? bridge.jid : null;
    }

    /**
     * Finds <tt>BridgeState</tt> for given pub-sub node.
     *
     * @param pubSubNode the name of pub-sub node to match with the bridge.
     *
     * @return <tt>BridgeState</tt> for given pub-sub node name.
     */
    private BridgeState findBridgeForNode(String pubSubNode)
    {
        String bridgeJid = pubSubToBridge.get(pubSubNode);
        if (bridgeJid != null)
        {
            return bridges.get(bridgeJid);
        }
        return null;
    }

    /**
     * Finds pub-sub node name for given videobridge JID.
     *
     * @param bridgeJid the JID of videobridge to be matched with
     *                  pub-sub node name.
     *
     * @return name of pub-sub node mapped for given videobridge JID.
     */
    private String findNodeForBridge(String bridgeJid)
    {
        for (Map.Entry<String, String> psNodeToBridge
            : pubSubToBridge.entrySet())
        {
            if (psNodeToBridge.getValue().equals(bridgeJid))
            {
                return psNodeToBridge.getKey();
            }
        }
        return null;
    }

    /**
     * Pub-sub notification processing logic.
     *
     * {@inheritDoc}
     */
    @Override
    public void onSubscriptionUpdate(String node, PacketExtension payload)
    {
        if (!(payload instanceof ColibriStatsExtension))
        {
            logger.error(
                "Unexpected pub-sub notification payload: "
                    + payload.getClass().getName());
            return;
        }

        BridgeState bridgeState = findBridgeForNode(node);
        if (bridgeState == null)
        {
            logger.warn(
                "No bridge registered or missing mapping for node: " + node);
            return;
        }

        ColibriStatsExtension stats = (ColibriStatsExtension) payload;
        for (PacketExtension child : stats.getChildExtensions())
        {
            if (!(child instanceof ColibriStatsExtension.Stat))
            {
                continue;
            }

            ColibriStatsExtension.Stat stat
                = (ColibriStatsExtension.Stat) child;
            if (VideobridgeStatistics.CONFERENCES.equals(stat.getName()))
            {
                Object statValue = stat.getValue();
                if (statValue == null)
                {
                    return;
                }
                String stringStatValue = String.valueOf(statValue);
                try
                {
                    bridgeState.setConferenceCount(
                        Integer.parseInt(stringStatValue));
                }
                catch(NumberFormatException e)
                {
                    logger.error(
                        "Error parsing conference count stat: "
                                + stringStatValue);
                }
            }
        }
    }

    /**
     * Returns the JID of pre-configured Jitsi Videobridge instance.
     */
    public String getPreConfiguredBridge()
    {
        return preConfiguredBridge;
    }

    /**
     * Sets the JID of pre-configured JVB instance which will be used when all
     * auto-detected bridges are down.
     * @param preConfiguredBridge XMPP address of pre-configured JVB component.
     */
    public void setPreConfiguredBridge(String preConfiguredBridge)
    {
        this.preConfiguredBridge = preConfiguredBridge;
    }

    /**
     * Class holds videobridge state and implements {@link java.lang.Comparable}
     * interface to find least loaded bridge.
     */
    class BridgeState
        implements Comparable<BridgeState>
    {
        /**
         * Videobridge XMPP address.
         */
        private final String jid;

        // If not set we consider it highly occupied,
        // because no stats we have been fetched so far.
        private int conferenceCount = Integer.MAX_VALUE;

        /**
         * Stores *operational* status which means it has been successfully used
         * by the focus to allocate the channels. It is reset to false when
         * focus fails to allocate channels, but it gets another chance when all
         * currently working bridges go down and might eventually get elevated
         * back to *operational* state.
         */
        private boolean isOperational = true /* we assume it is operational */;

        BridgeState(String bridgeJid)
        {
            if (StringUtils.isNullOrEmpty(bridgeJid))
                throw new NullPointerException("bridgeJid");

            this.jid = bridgeJid;
        }

        public void setConferenceCount(int conferenceCount)
        {
            if (this.conferenceCount != conferenceCount)
            {
                logger.info(
                    "Conference count for: " + jid + ": " + conferenceCount);
            }
            this.conferenceCount = conferenceCount;
        }

        public int getConferenceCount()
        {
            return this.conferenceCount;
        }

        public void setIsOperational(boolean isOperational)
        {
            this.isOperational = isOperational;
        }

        /**
         * The least value is returned the least the bridge is loaded.
         *
         * {@inheritDoc}
         */
        @Override
        public int compareTo(BridgeState o)
        {
            if (this.isOperational && !o.isOperational)
                return -1;
            else if (!this.isOperational && o.isOperational)
                return 1;

            return conferenceCount - o.conferenceCount;
        }
    }
}
