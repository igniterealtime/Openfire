/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jirecon.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;

import org.jitsi.protocol.xmpp.*;

import java.util.*;

/**
 * Class handles discovery of Jitsi Meet application services like bridge,
 * recording, SIP gateway and so on...
 *
 * @author Pawel Domas
 */
public class JitsiMeetServices
    implements RegistrationStateChangeListener
{
    /**
     * The logger
     */
    private final static Logger logger
        = Logger.getLogger(JitsiMeetServices.class);

    /**
     * Feature set advertised by videobridge.
     */
    public static final String[] VIDEOBRIDGE_FEATURES = new String[]
        {
            ColibriConferenceIQ.NAMESPACE,
            ProtocolProviderServiceJabberImpl
                .URN_XMPP_JINGLE_DTLS_SRTP,
            ProtocolProviderServiceJabberImpl
                .URN_XMPP_JINGLE_ICE_UDP_1,
            ProtocolProviderServiceJabberImpl
                .URN_XMPP_JINGLE_RAW_UDP_0
        };

    /**
     * Features advertised by Jirecon recorder container.
     */
    private static final String[] JIRECON_RECORDER_FEATURES = new String[]
        {
            JireconIqProvider.NAMESPACE
        };

    /**
     * Features advertised by SIP gateway component.
     */
    private static final String[] SIP_GW_FEATURES = new String[]
        {
            "http://jitsi.org/protocol/jigasi",
            "urn:xmpp:rayo:0"
        };

    /**
     * Features used to recognize pub-sub service.
     */
    private static final String[] PUBSUB_FEATURES = new String[]
        {
            "http://jabber.org/protocol/pubsub",
            "http://jabber.org/protocol/pubsub#subscribe"
        };

    /**
     * Capabilities operation set used to discover services info.
     */
    private OperationSetSimpleCaps capsOpSet;

    /**
     * XMPP xmppDomain for which we're discovering service info.
     */
    private String xmppDomain;

    /**
     * Videobridge component XMPP address.
     */
    private BridgeSelector bridgeSelector;

    /**
     * The protocol service handler that provides XMPP service.
     */
    private ProtocolProviderHandler protocolProviderHandler;

    /**
     * Jirecon recorder component XMPP address.
     */
    private String jireconRecorder;

    /**
     * SIP gateway component XMPP address.
     */
    private String sipGateway;

    /**
     * Starts this instance.
     *
     * @param xmppDomain server address/main service XMPP xmppDomain that hosts
     *                      the conference system.
     * @param xmppAuthDomain the xmppDomain used for XMPP authentication.
     * @param xmppUserName the user name used to login.
     * @param xmppLoginPassword the password used for authentication.
     *
     * @throws java.lang.IllegalStateException if started already.
     */
    public void start(String serverAddress,
                      String xmppDomain,
                      String xmppAuthDomain,
                      String xmppUserName,
                      String xmppLoginPassword)
    {
        if (protocolProviderHandler != null)
        {
            throw new IllegalStateException("Already started");
        }

        this.xmppDomain = xmppDomain;

        this.protocolProviderHandler
            = new ProtocolProviderHandler();

        protocolProviderHandler.start(
            serverAddress, xmppAuthDomain, xmppLoginPassword,
            xmppUserName, this);

        this.capsOpSet
            = protocolProviderHandler.getOperationSet(
                    OperationSetSimpleCaps.class);

        this.bridgeSelector
            = new BridgeSelector(
                    protocolProviderHandler
                        .getProtocolProvider()
                        .getOperationSet(OperationSetSubscription.class));

        if (protocolProviderHandler.isRegistered())
        {
            init();
        }
        else
        {
            protocolProviderHandler.register();
        }
    }

    /**
     * Stops this instance and disposes XMPP connection.
     */
    public void stop()
    {
        if (protocolProviderHandler != null)
        {
            protocolProviderHandler.stop();

            protocolProviderHandler = null;
        }
    }

    /**
     * Initializes this instance and discovers Jitsi Meet services.
     */
    public void init()
    {
        List<String> items = capsOpSet.getItems(xmppDomain);
        for (String item : items)
        {
            if (capsOpSet.hasFeatureSupport(item, VIDEOBRIDGE_FEATURES))
            {
                logger.info("Discovered videobridge: " + item);

                bridgeSelector.addJvbAddress(item);
            }
            else if (jireconRecorder == null
                && capsOpSet.hasFeatureSupport(item, JIRECON_RECORDER_FEATURES))
            {
                logger.info("Discovered Jirecon recorder: " + item);

                setJireconRecorder(item);
            }
            else if (sipGateway == null
                && capsOpSet.hasFeatureSupport(item, SIP_GW_FEATURES))
            {
                logger.info("Discovered SIP gateway: " + item);

                setSipGateway(item);
            }
            /*
            FIXME: pub-sub service auto-detect ?
            else if (capsOpSet.hasFeatureSupport(item, PUBSUB_FEATURES))
            {
                // Potential PUBSUB service
                logger.info("Potential PUBSUB service:" + item);
                List<String> subItems = capsOpSet.getItems(item);
                for (String subItem: subItems)
                {
                    logger.info("Subnode " + subItem + " of " + item);
                    capsOpSet.hasFeatureSupport(
                        item, subItem, VIDEOBRIDGE_FEATURES);
                }
            }*/
        }
    }

    /**
     * Returns the XMPP address of videobridge component.
     */
    public String getVideobridge()
    {
        return bridgeSelector.selectVideobridge();
    }

    /**
     * Sets new XMPP address of the SIP gateway component.
     * @param sipGateway the XMPP address to be set as SIP gateway component
     *                   address.
     */
    private void setSipGateway(String sipGateway)
    {
        this.sipGateway = sipGateway;
    }

    /**
     * Sets new XMPP address of the Jirecon jireconRecorder component.
     * @param jireconRecorder the XMPP address to be set as Jirecon recorder
     *                        component address.
     */
    public void setJireconRecorder(String jireconRecorder)
    {
        this.jireconRecorder = jireconRecorder;
    }

    /**
     * Returns the XMPP address of Jirecon recorder component.
     */
    public String getJireconRecorder()
    {
        return jireconRecorder;
    }

    /**
     * Returns {@link BridgeSelector} bound to this instance that can be used to
     * select the videobridge on the xmppDomain handled by this instance.
     */
    public BridgeSelector getBridgeSelector()
    {
        return bridgeSelector;
    }

    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        //FIXME: do something here (start PBU-SUB)
        if (RegistrationState.REGISTERED.equals(evt.getNewState()))
        {
            init();
        }
    }

    /**
     * Returns capabilities operation set used by this instance to discover
     * services info.
     */
    public OperationSetSimpleCaps getCapsOpSet()
    {
        return capsOpSet;
    }
}
