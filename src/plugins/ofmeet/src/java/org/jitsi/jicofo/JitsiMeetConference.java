/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriConferenceIQ.Recording.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.jinglesdp.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;
import net.java.sip.communicator.util.Logger;

import org.jitsi.jicofo.log.*;
import org.jitsi.jicofo.recording.*;
import org.jitsi.jicofo.util.*;
import org.jitsi.protocol.*;
import org.jitsi.protocol.xmpp.*;
import org.jitsi.protocol.xmpp.extensions.*;
import org.jitsi.protocol.xmpp.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.videobridge.log.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.Message;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

import org.jitsi.jigasi.openfire.CallControlComponent;		// BAO


/**
 * Class represents the focus of Jitsi Meet conference. Responsibilities:
 * a) Invites peers to the conference once they join multi user chat room
 *    (establishes Jingle session with peer).
 * b) Manages colibri channels per peer.
 * c) Advertisement of changes in peer's SSRCs. When new peer joins the
 * 'add-source' notification is being sent, on leave: 'remove-source'
 * and a combination of add/remove on stream switch(desktop sharing).
 *
 * @author Pawel Domas
 */
public class JitsiMeetConference
    implements RegistrationStateChangeListener,
               JingleRequestHandler

{
    /**
     * The logger instance used by this class.
     */
    private final static Logger logger
        = Logger.getLogger(JitsiMeetConference.class);

    /**
     * FIXME: remove and replace with focusUserName which is already available
     * The constant describes focus MUC nickname
     */
    private final static String FOCUS_NICK = "focus";

    /**
     * Error code used in {@link OperationFailedException} when there are no
     * working videobridge bridges.
     * FIXME: consider moving to OperationFailedException ?
     */
    private final static int BRIDGE_FAILURE_ERR_CODE = 20;

    /**
     * Name of MUC room that is hosting Jitsi Meet conference.
     */
    private final String roomName;

    /**
     * The address of XMPP server to which the focus user will connect to.
     */
    private final String serverAddress;

    /**
     * The name of XMPP domain used by the focus user to login.
     */
    private final String xmppDomain;

    /**
     * The password user by the focus to login
     * (if null then will login anonymously).
     */
    private final String xmppLoginPassword;

    /**
     * {@link ConferenceListener} that will be notified about conference events.
     */
    private final ConferenceListener listener;

    /**
     * The instance of conference configuration.
     */
    private final JitsiMeetConfig config;

    /**
     * XMPP protocol provider handler used by the focus.
     */
    private ProtocolProviderHandler protocolProviderHandler
        = new ProtocolProviderHandler();

    /**
     * Chat room operation set used to handle MUC stuff.
     */
    private OperationSetMultiUserChat chatOpSet;

    /**
     * Conference room chat instance.
     */
    private ChatRoom chatRoom;

    /**
     * Operation set used to handle Jingle sessions with conference peers.
     */
    private OperationSetJingle jingle;

    /**
     * Colibri operation set used to manage videobridge channels allocations.
     */
    private OperationSetColibriConference colibri;

    /**
     * Jitsi Meet tool used for specific operations like adding presence
     * extensions.
     */
    private OperationSetJitsiMeetTools meetTools;

    /**
     * The list of active conference participants.
     */
    private final List<Participant> participants
        = new CopyOnWriteArrayList<Participant>();

    /**
     * Operation set used for service discovery.
     */
    private OperationSetSimpleCaps disco;

    /**
     * Information about Jitsi Meet conference services like videobridge,
     * SIP gateway, Jirecon.
     */
    private JitsiMeetServices services;

    /**
     * Handler that takes care of pre-processing various Jitsi Meet extensions
     * IQs sent from conference participants to the focus.
     */
    private MeetExtensionsHandler meetExtensionsHandler;

    /**
     * Recording functionality implementation.
     */
    private Recorder recorder;

    /**
     * Chat room roles and presence handler.
     */
    private ChatRoomRoleAndPresence presenceHandler;

    /**
     * Indicates if this instance has been started(initialized).
     */
    private boolean started;

    /**
     * Idle timestamp for this focus, -1 means active, otherwise
     * System.currentTimeMillis() is set when focus becomes idle.
     * Used to detect idle session and expire it if idle time limit is exceeded.
     */
    private long idleTimestamp = -1;

    /**
     * The <tt>PacketListener</tt> which we use to handle incoming
     * <tt>message</tt> stanzas.
     */
    private final PacketListener messageListener
        = new MessageListener();

    /**
     * Keeps a record whether user has activated recording before other
     * participants has joined and the actual conference has been created.
     */
    private RecordingState earlyRecordingState = null;

    /**
     * Creates new instance of {@link JitsiMeetConference}.
     *
     * @param roomName name of MUC room that is hosting the conference.
     * @param serverAddress the address of the XMPP server.
     * @param xmppDomain optional XMPP domain if different than
     *        <tt>serverAddress</tt>
     * @param xmppLoginPassword optional XMPP focus user password
     *        (if <tt>null</tt> then focus user will connect anonymously).
     * @param listener the listener that will be notified about this instance
     *        events.
     * @param config the conference configuration instance.
     */
    public JitsiMeetConference(String roomName,
                               String serverAddress,
                               String xmppDomain,
                               String xmppLoginPassword,
                               ConferenceListener listener,
                               JitsiMeetConfig config)
    {
        this.roomName = roomName;
        this.serverAddress = serverAddress;
        this.xmppDomain = xmppDomain != null ? xmppDomain : serverAddress;
        this.xmppLoginPassword = xmppLoginPassword;
        this.listener = listener;
        this.config = config;
    }

    /**
     * Creates new instance of {@link JitsiMeetConference}.
     *
     * @param roomName name of MUC room that is hosting the conference.
     * @param serverAddress name of the XMPP server.
     * @param listener the listener that will be notified about this instance
     *        events.
     */
    // FIXME: why is not used now ? remove eventually
    public JitsiMeetConference(String roomName,
                               String serverAddress,
                               ConferenceListener listener)
    {
        this(roomName, serverAddress, null, null, listener,
             new JitsiMeetConfig(new HashMap<String, String>()));
    }

    /**
     * Starts conference focus processing, bind listeners and so on...
     *
     * @throws Exception if error occurs during initialization. Instance is
     *         considered broken in that case.
     */
    public synchronized void start()
        throws Exception
    {
        if (started)
            return;

        protocolProviderHandler.start(
            serverAddress, xmppDomain, xmppLoginPassword, FOCUS_NICK, this);

        colibri
            = protocolProviderHandler.getOperationSet(
                    OperationSetColibriConference.class);

        colibri.setJitsiMeetConfig(config);

        jingle
            = protocolProviderHandler.getOperationSet(
                    OperationSetJingle.class);

        jingle.setRequestHandler(this);

        chatOpSet
            = protocolProviderHandler.getOperationSet(
                    OperationSetMultiUserChat.class);

        disco
            = protocolProviderHandler.getOperationSet(
                    OperationSetSimpleCaps.class);

        meetTools
            = protocolProviderHandler.getOperationSet(
                    OperationSetJitsiMeetTools.class);

        meetExtensionsHandler = new MeetExtensionsHandler(this);

        services
            = ServiceUtils.getService(
                    FocusBundleActivator.bundleContext,
                    JitsiMeetServices.class);

        // Set pre-configured videobridge
        services.getBridgeSelector()
            .setPreConfiguredBridge(config.getPreConfiguredVideobridge());

        if (!protocolProviderHandler.isRegistered())
        {
            protocolProviderHandler.register();
        }
        else
        {
            joinTheRoom();
        }

        idleTimestamp = System.currentTimeMillis();

        started = true;
    }

    /**
     * Returns <tt>true</tt> if focus has joined the conference room.
     */
    public boolean isInTheRoom()
    {
        return chatRoom != null && chatRoom.isJoined();
    }

    /**
     * Checks if it's the right time to join the room and does it eventually.
     */
    private void maybeJoinTheRoom()
    {
        if (chatRoom == null && protocolProviderHandler.isRegistered())
        {
            logger.info("Registered: " + protocolProviderHandler);

            joinTheRoom();
        }
    }

    /**
     * Joins the conference room.
     */
    private void joinTheRoom()
    {
        logger.info("Joining the room: " + roomName);

        try
        {
            chatRoom = chatOpSet.findRoom(roomName);

            presenceHandler = new ChatRoomRoleAndPresence(this, chatRoom);
            presenceHandler.init();

            chatRoom.join();

            meetExtensionsHandler.init();
        }
        catch (Exception e)
        {
            logger.error(e, e);

            stop();
        }
    }

    private OperationSetDirectSmackXmpp getDirectXmppOpSet()
    {
        return protocolProviderHandler.getOperationSet(
            OperationSetDirectSmackXmpp.class);
    }

    /**
     * Lazy initializer for {@link #recorder}. If there is Jirecon component
     * service available then {@link JireconRecorder} is used. Otherwise we fall
     * back to direct videobridge communication through {@link JvbRecorder}.
     *
     * @return {@link Recorder} implementation used by this instance.
     */
    private Recorder getRecorder()
    {
        if (recorder == null)
        {
            OperationSetDirectSmackXmpp xmppOpSet
                = protocolProviderHandler.getOperationSet(
                        OperationSetDirectSmackXmpp.class);

            String recorderService = services.getJireconRecorder();
            if (!StringUtils.isNullOrEmpty(recorderService))
            {
                recorder
                    = new JireconRecorder(
                            getFocusJid(),
                            services.getJireconRecorder(), xmppOpSet);
            }
            else
            {
                logger.warn("No recorder service discovered - using JVB");

                recorder
                    = new JvbRecorder(
                            colibri.getConferenceId(),
                            services.getVideobridge(), xmppOpSet);
            }
        }
        return recorder;
    }

    /**
     * Leaves the conference room.
     */
    private void leaveTheRoom()
    {
        if (chatRoom == null)
        {
            logger.error("Chat room already left!");
            return;
        }

        if (presenceHandler != null)
        {
            presenceHandler.dispose();
            presenceHandler = null;
        }

        chatRoom.leave();

        chatRoom = null;
    }

    /**
     * Method called by {@link #presenceHandler} when new member joins
     * the conference room.
     *
     * @param chatRoomMember the new member that has just joined the room.
     */
    protected void onMemberJoined(final ChatRoomMember chatRoomMember)
    {
        logger.info("Member " + chatRoomMember.getName()
                        + " joined " + chatRoom.getName());

        idleTimestamp = -1;

        if (!initConference())
            return;

        // Invite peer takes time because of channel allocation, so schedule
        // this on separate thread
        FocusBundleActivator
            .getSharedThreadPool()
            .submit(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        inviteChatMember(chatRoomMember);
                    }
                });
    }

    /**
     * Invites new member to the conference which means new Jingle session
     * established and videobridge channels being allocated.
     *
     * @param chatRoomMember the chat member to be invited into the conference.
     */
    private void inviteChatMember(ChatRoomMember chatRoomMember)
    {
        if (isFocusMember(chatRoomMember))
            return;

        if (findParticipantForChatMember(chatRoomMember) != null)
            return;

        logger.info("Inviting " + chatRoomMember.getContactAddress());

        String address = chatRoomMember.getContactAddress();

        Participant newParticipant
            = new Participant(
                    (XmppChatMember) chatRoomMember);

        participants.add(newParticipant);

/*		BAO

        // Detect bundle support
        newParticipant.setHasBundleSupport(
            disco.hasFeatureSupport(
                address,
                new String[]{
                    "urn:ietf:rfc:5761",  	// rtcp-mux
                    "urn:ietf:rfc:5888"		// bundle
                }));

        // Is it SIP gateway ?
        newParticipant.setIsSipGateway(
            disco.hasFeatureSupport(
                address,
                new String[] { "http://jitsi.org/protocol/jigasi" }));

        logger.info(
            chatRoomMember.getContactAddress()
                + " has bundle ? "
                + newParticipant.hasBundleSupport());
*/
        try
        {
            List<ContentPacketExtension> offer = createOffer(newParticipant);

            jingle.initiateSession(
                    newParticipant.hasBundleSupport(), address, offer);
        }
        catch (OperationFailedException e)
        {
            //FIXME: retry ? sometimes it's just timeout
            logger.error(
                "Failed to invite " + chatRoomMember.getContactAddress(), e);

            participants.remove(newParticipant);

            // Notify users about bridge is down event
            if (BRIDGE_FAILURE_ERR_CODE == e.getErrorCode())
            {
                meetTools.sendPresenceExtension(
                    chatRoom, new BridgeIsDownPacketExt());
            }
        }
    }

    /**
     * Allocates Colibri channels for given {@link Participant} by trying all
     * available bridges returned by {@link BridgeSelector}.
     *
     * @param peer the for whom Colibri channel are to be allocated.
     * @param contents the media offer description passed to the bridge.
     *
     * @return {@link ColibriConferenceIQ} that describes channels allocated for
     *         given <tt>peer</tt>.
     *
     * @throws OperationFailedException if we have failed to allocate channels
     *         using existing bridge and we can not switch to another bridge.
     */
    private ColibriConferenceIQ allocateChannels(
            Participant peer, List<ContentPacketExtension> contents)
        throws OperationFailedException
    {
        // Allocate by trying all bridges on prioritized list
        BridgeSelector bridgeSelector = services.getBridgeSelector();

        Iterator<String> bridgesIterator
            = bridgeSelector.getPrioritizedBridgesList().iterator();

        // Set initial bridge if we haven't used any yet
        if (StringUtils.isNullOrEmpty(colibri.getJitsiVideobridge()))
        {
            if (!bridgesIterator.hasNext())
            {
                throw new OperationFailedException(
                    "Failed to allocate channels - no bridge configured",
                    OperationFailedException.GENERAL_ERROR);
            }

            colibri.setJitsiVideobridge(
                bridgesIterator.next());
        }

        boolean conferenceExists = colibri.getConferenceId() != null;
        while (true)
        {
            try
            {
                ColibriConferenceIQ peerChannels
                    = colibri.createColibriChannels(
                            peer.hasBundleSupport(),
                            peer.getChatMember().getName(),
                            true, contents);

                bridgeSelector.updateBridgeOperationalStatus(
                    colibri.getJitsiVideobridge(), true);

                if (!conferenceExists)
                {
                    // If conferenceId is returned at this point it means that
                    // the conference has just been created, so we log it.
                    String conferenceId = colibri.getConferenceId();
                    if (conferenceId != null)
                    {
                        LoggingService loggingService
                                = FocusBundleActivator.getLoggingService();
                        if (loggingService != null)
                        {
                            loggingService.logEvent(
                                LogEventFactory.conferenceRoom(
                                        conferenceId,
                                        roomName));
                        }

                        CallControlComponent.self.conferences.put(roomName, conferenceId);	// BAO
                    }
                }
                return peerChannels;
            }
            catch(OperationFailedException exc)
            {
                String faultyBridge = colibri.getJitsiVideobridge();

                logger.error(
                    "Failed to allocate channels using bridge: "
                        + colibri.getJitsiVideobridge(), exc);

                bridgeSelector.updateBridgeOperationalStatus(
                    faultyBridge, false);

                // Check if the conference is in progress
                if (!StringUtils.isNullOrEmpty(colibri.getConferenceId()))
                {
                    // Restart
                    logger.error("Bridge failure - stopping the conference");
                    stop();
                }
                else if (!bridgesIterator.hasNext())
                {
                    // No more bridges to try
                    throw new OperationFailedException(
                        "Failed to allocate channels - all bridges are faulty",
                        BRIDGE_FAILURE_ERR_CODE);
                }
                else
                {
                    // Try next bridge
                    colibri.setJitsiVideobridge(bridgesIterator.next());
                }
            }
        }
    }

    /**
     * Creates Jingle offer for given {@link Participant}.
     *
     * @param peer the participant for whom Jingle offer will be created.
     *
     * @return the list of contents describing conference Jingle offer.
     *
     * @throws OperationFailedException if focus fails to allocate channels
     *         or something goes wrong.
     */
    private List<ContentPacketExtension> createOffer(Participant peer)
        throws OperationFailedException
    {
        List<ContentPacketExtension> contents
            = new ArrayList<ContentPacketExtension>();

        boolean enableFirefoxHacks
                = config == null || config.enableFirefoxHacks() == null
                    ? false : config.enableFirefoxHacks();

        contents.add(
            JingleOfferFactory.createContentForMedia(MediaType.AUDIO,
                    enableFirefoxHacks));

        contents.add(
            JingleOfferFactory.createContentForMedia(MediaType.VIDEO,
                    enableFirefoxHacks));

        boolean openSctp = config == null || config.openSctp() == null
                ? true : config.openSctp();

        if (openSctp)
        {
            contents.add(
                    JingleOfferFactory.createContentForMedia(MediaType.DATA,
                            enableFirefoxHacks));
        }

        boolean useBundle = peer.hasBundleSupport();

        ColibriConferenceIQ peerChannels = allocateChannels(peer, contents);

        if (peerChannels == null)
            return null;

        peer.setColibriChannelsInfo(peerChannels);

        for (ContentPacketExtension cpe : contents)
        {
            ColibriConferenceIQ.Content colibriContent
                = peerChannels.getContent(cpe.getName());

            if (colibriContent == null)
                continue;

            // Channels
            for (ColibriConferenceIQ.Channel channel
                : colibriContent.getChannels())
            {
                IceUdpTransportPacketExtension transport;

                if (useBundle)
                {
                    ColibriConferenceIQ.ChannelBundle bundle
                        = peerChannels.getChannelBundle(
                                channel.getChannelBundleId());

                    if (bundle == null)
                    {
                        logger.error(
                            "No bundle for " + channel.getChannelBundleId());
                        continue;
                    }

                    transport = bundle.getTransport();

                    if (!transport.isRtcpMux())
                    {
                        transport.addChildExtension(
                            new RtcpmuxPacketExtension());
                    }
                }
                else
                {
                    transport = channel.getTransport();
                }

                try
                {
                    // Remove empty transport
                    IceUdpTransportPacketExtension empty
                        = cpe.getFirstChildOfType(
                                IceUdpTransportPacketExtension.class);
                    cpe.getChildExtensions().remove(empty);

                    cpe.addChildExtension(
                        IceUdpTransportPacketExtension
                            .cloneTransportAndCandidates(transport, true));
                }
                catch (Exception e)
                {
                    logger.error(e, e);
                }

            }
            // SCTP connections
            for (ColibriConferenceIQ.SctpConnection sctpConn
                : colibriContent.getSctpConnections())
            {
                IceUdpTransportPacketExtension transport;

                if (useBundle)
                {
                    ColibriConferenceIQ.ChannelBundle bundle
                        = peerChannels.getChannelBundle(
                                sctpConn.getChannelBundleId());

                    if (bundle == null)
                    {
                        logger.error(
                            "No bundle for " + sctpConn.getChannelBundleId());
                        continue;
                    }

                    transport = bundle.getTransport();
                }
                else
                {
                    transport = sctpConn.getTransport();
                }

                try
                {
                    // Remove empty transport
                    IceUdpTransportPacketExtension empty
                        = cpe.getFirstChildOfType(
                                IceUdpTransportPacketExtension.class);
                    cpe.getChildExtensions().remove(empty);

                    IceUdpTransportPacketExtension copy
                        = IceUdpTransportPacketExtension
                            .cloneTransportAndCandidates(transport, true);

                    // FIXME: hardcoded
                    SctpMapExtension sctpMap = new SctpMapExtension();
                    sctpMap.setPort(5000);
                    sctpMap.setProtocol(
                            SctpMapExtension.Protocol.WEBRTC_CHANNEL);
                    sctpMap.setStreams(1024);

                    copy.addChildExtension(sctpMap);

                    cpe.addChildExtension(copy);
                }
                catch (Exception e)
                {
                    logger.error(e, e);
                }
            }
            // Existing peers SSRCs
            RtpDescriptionPacketExtension rtpDescPe
                = JingleUtils.getRtpDescription(cpe);
            if (rtpDescPe != null)
            {
                if (useBundle)
                {
                    // rtcp-mux
                    rtpDescPe.addChildExtension(
                        new RtcpmuxPacketExtension());
                }

                // Include all peers SSRCs
                List<SourcePacketExtension> mediaSources
                    = getAllSSRCs(cpe.getName());

                for (SourcePacketExtension ssrc : mediaSources)
                {
                    try
                    {
                        rtpDescPe.addChildExtension(
                            ssrc.copy());
                    }
                    catch (Exception e)
                    {
                        logger.error("Copy SSRC error", e);
                    }
                }

                // Include SSRC groups
                List<SourceGroupPacketExtension> sourceGroups
                    = getAllSSRCGroups(cpe.getName());
                for(SourceGroupPacketExtension ssrcGroup : sourceGroups)
                {
                    rtpDescPe.addChildExtension(ssrcGroup);
                }

                // Copy SSRC sent from the bridge(only the first one)
                for (ColibriConferenceIQ.Channel channel
                        : colibriContent.getChannels())
                {
                    SourcePacketExtension ssrcPe
                        = channel.getSources().size() > 0
                            ? channel.getSources().get(0) : null;
                    if (ssrcPe != null)
                    {
                        try
                        {
                            String contentName = colibriContent.getName();
                            SourcePacketExtension ssrcCopy = ssrcPe.copy();

                            // FIXME: not all parameters are used currently
                            ssrcCopy.addParameter(
                                new ParameterPacketExtension(
                                    "cname","mixed"));
                            ssrcCopy.addParameter(
                                new ParameterPacketExtension(
                                    "label",
                                    "mixedlabel" + contentName + "0"));
                            ssrcCopy.addParameter(
                                new ParameterPacketExtension(
                                    "msid",
                                    "mixedmslabel mixedlabel"
                                            + contentName + "0"));
                            ssrcCopy.addParameter(
                                new ParameterPacketExtension(
                                    "mslabel","mixedmslabel"));

                            rtpDescPe.addChildExtension(ssrcCopy);
                        }
                        catch (Exception e)
                        {
                            logger.error("Copy SSRC error", e);
                        }
                    }
                }
            }
        }

        return contents;
    }

    /**
     * Initializes the conference by inviting first participants.
     *
     * @return <tt>false</tt> if it's too early to start, or <tt>true</tt>
     *         if the conference has started.
     */
    private boolean initConference()
    {
        if (!checkAtLeastTwoParticipants())
            return false;

        for (ChatRoomMember member : chatRoom.getMembers())
        {
            inviteChatMember(member);
        }

        return true;
    }

    /**
     * Counts the number of non-focus chat room members and returns
     * <tt>true</tt> if there are at least two of them.
     *
     * @return <tt>true</tt> if we have at least two non-focus participants.
     */
    private boolean checkAtLeastTwoParticipants()
    {
        // 2 + 1 focus
        if (chatRoom.getMembersCount() >= (2 + 1))
            return true;

        int realCount = 0;
        for (ChatRoomMember member : chatRoom.getMembers())
        {
            if (!isFocusMember(member))
                realCount++;
        }

        return realCount >= 2;
    }

    /**
     * Counts human participants in the conference by excluding focus, SIP
     * gateway and other service participants in future.
     *
     * // TODO: also exclude Jirecon participant
     *
     * @return the number of human participants in the conference excluding
     *         service participants like the focus or SIP gateway.
     */
    private boolean checkAtLeastOneHumanParticipants()
    {
        int humanCount = 0;
        for (Participant participant : participants)
        {
            if (!isFocusMember(participant.getChatMember())
                && !participant.isSipGateway())
                humanCount++;
        }
        return humanCount > 0;
    }

    /**
     * Check if given chat member is a focus.
     *
     * @param member the member to check.
     *
     * @return <tt>true</tt> if given {@link ChatRoomMember} is a focus
     *         participant.
     */
    static boolean isFocusMember(ChatRoomMember member)
    {
        return member.getName().equals("focus");
    }

    /**
     * Check if given member represent SIP gateway participant.

     * @param member the chat member to be checked.
     *
     * @return <tt>true</tt> if given <tt>member</tt> represents the SIP gateway
     */
    boolean isSipGateway(ChatRoomMember member)
    {
        Participant participant = findParticipantForChatMember(member);

        return participant != null && participant.isSipGateway();
    }

    /**
     * Expires the conference on the bridge and other stuff realted to it.
     */
    private void disposeConference()
    {
        // FIXME: Does it make sense to put recorder here ?
        if (recorder != null)
        {
            recorder.dispose();
            recorder = null;
        }

        meetExtensionsHandler.dispose();

        colibri.expireConference();
    }

    /**
     * Method called by {@link #presenceHandler} when one of the members has
     * been kicked out of the conference room.
     *
     * @param chatRoomMember kicked chat room member.
     */
    protected void onMemberKicked(ChatRoomMember chatRoomMember)
    {
        logger.info("Member " + chatRoomMember.getName()
                        + " kicked !!! " + chatRoom.getName());
        /*
        FIXME: terminate will have no effect, as peer's MUC address
         will be no longer active.
        Participant session = findParticipantForChatMember(chatRoomMember);
        if (session != null)
        {
            jingle.terminateSession(
                session.getJingleSession(), Reason.EXPIRED);
        }
        else
        {
            logger.warn("No active session with "
                            + chatRoomMember.getContactAddress());
        }*/

        onMemberLeft(chatRoomMember);
    }

    /**
     * Method called by {@link #presenceHandler} when someone leave conference
     * chat room.
     *
     * @param chatRoomMember the member that has left the room.
     */
    synchronized protected void onMemberLeft(ChatRoomMember chatRoomMember)
    {
        logger.info("Member " + chatRoomMember.getName()
                        + " left " + chatRoom.getName());

        Participant leftPeer = findParticipantForChatMember(chatRoomMember);
        if (leftPeer != null)
        {
            JingleSession peerJingleSession = leftPeer.getJingleSession();
            if (peerJingleSession != null)
            {
                logger.info(
                    "Hanging up member " + chatRoomMember.getContactAddress());

                removeSSRCs(peerJingleSession,
                            leftPeer.getSSRCsCopy(),
                            leftPeer.getSSRCGroupsCopy());

                ColibriConferenceIQ peerChannels
                    = leftPeer.getColibriChannelsInfo();
                if (peerChannels != null)
                {
                    colibri.expireChannels(leftPeer.getColibriChannelsInfo());
                }
                //jingle.terminateSession(session.getJingleSession());
            }
            participants.remove(leftPeer);
        }

        if (!checkAtLeastOneHumanParticipants())
        {
            // Terminate all other participants
            for (Participant participant : participants)
            {
                try
                {
                    terminateParticipant(participant);
                }
                catch (OperationFailedException e)
                {
                    logger.error(e, e);
                    // Dispose the focus on failure (we would have done this
                    // anyway later after "member left" events are handled)
                    stop();
                    break;
                }
            }
        }

        if (participants.size() == 0)
        {
            stop();
        }
    }

    /**
     * Stops the conference, disposes colibri channels and releases all
     * resources used by the focus.
     */
    synchronized void stop()
    {
        if (!started)
            return;

        getDirectXmppOpSet().removePacketHandler(messageListener);

        disposeConference();

        leaveTheRoom();

        disposeAccount();

        listener.conferenceEnded(this);

        started = false;
    }

    /**
     * Destroys focus XMPP account.
     */
    private void disposeAccount()
    {
        jingle.setRequestHandler(null);

        protocolProviderHandler.stop();
    }

    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        logger.info("Reg state changed: " + evt);

        if (RegistrationState.REGISTERED.equals(evt.getNewState()))
        {
            getDirectXmppOpSet().addPacketHandler(
                messageListener,
                new MessageTypeFilter(Message.Type.normal));
        }
        maybeJoinTheRoom();
    }

    private Participant findParticipantForJingleSession(
            JingleSession jingleSession)
    {
        for (Participant participant : participants)
        {
            if (participant.getChatMember()
                .getContactAddress().equals(jingleSession.getAddress()))
                return participant;
        }
        return null;
    }

    private Participant findParticipantForChatMember(ChatRoomMember chatMember)
    {
        for (Participant participant : participants)
        {
            if (participant.getChatMember().equals(chatMember))
                return participant;
        }
        return null;
    }

    private Participant findParticipantForJabberId(String jid)
    {
        for (Participant participant : participants)
        {
            String peerJid = participant.getChatMember().getJabberID();
            if (peerJid != null && peerJid.equals(jid))
            {
                return participant;
            }
        }
        return null;
    }

    Participant findParticipantForRoomJid(String roomJid)
    {
        for (Participant participant : participants)
        {
            String peerRoomJid = participant.getJingleSession().getAddress();
            if (peerRoomJid != null && peerRoomJid.equals(roomJid))
            {
                return participant;
            }
        }
        return null;
    }

    private Participant findParticipantForMucAddress(String mucAddress)
    {
        for (Participant participant : participants)
        {
            if (participant.getChatMember()
                    .getContactAddress().equals(mucAddress))
            {
                return participant;
            }
        }
        return null;
    }

    private void terminateParticipant(Participant participant)
        throws OperationFailedException
    {
        JingleSession session = participant.getJingleSession();
        if (session != null)
        {
            jingle.terminateSession(session, Reason.EXPIRED);
        }

        // Kick out of the room
        chatRoom.kickParticipant(
            participant.getChatMember(),
            "End of the conference");
    }

    /**
     * Callback called when 'session-accept' is received from invited
     * participant.
     *
     * {@inheritDoc}
     */
    @Override
    public void onSessionAccept( JingleSession peerJingleSession,
                                 List<ContentPacketExtension> answer)
    {
        Participant participant
            = findParticipantForJingleSession(peerJingleSession);
        if (participant.getJingleSession() != null)
        {
            //FIXME: we should reject it ?
            logger.error(
                "Reassigning jingle session for participant: "
                        + peerJingleSession.getAddress());
        }

        participant.setJingleSession(peerJingleSession);

        participant.addSSRCsFromContent(answer);

        participant.addSSRCGroupsFromContent(answer);

        // Update SSRC groups
        colibri.updateSsrcGroupsInfo(
            participant.getSSRCGroupsCopy(),
            participant.getColibriChannelsInfo());

        logger.info("Got SSRCs from " + peerJingleSession.getAddress());

        for (Participant peerToNotify : participants)
        {
            JingleSession jingleSessionToNotify
                    = peerToNotify.getJingleSession();
            if (jingleSessionToNotify == null)
            {
                logger.warn(
                    "No jingle session yet for "
                        + peerToNotify.getChatMember().getContactAddress());

                peerToNotify.scheduleSSRCsToAdd(participant.getSSRCS());

                peerToNotify.scheduleSSRCGroupsToAdd(
                    participant.getSSRCGroups());

                continue;
            }

            // Skip origin
            if (peerJingleSession.equals(jingleSessionToNotify))
                continue;

            jingle.sendAddSourceIQ(
                participant.getSSRCS(),
                participant.getSSRCGroups(),
                jingleSessionToNotify);
        }

        // Notify the peer itself since it is now stable
        if (participant.hasSsrcsToAdd())
        {
            jingle.sendAddSourceIQ(
                    participant.getSsrcsToAdd(),
                    participant.getSSRCGroupsToAdd(),
                    peerJingleSession);

            participant.clearSsrcsToAdd();
        }
        if (participant.hasSsrcsToRemove())
        {
            jingle.sendRemoveSourceIQ(
                    participant.getSsrcsToRemove(),
                    participant.getSsrcGroupsToRemove(),
                    peerJingleSession);

            participant.clearSsrcsToRemove();
        }

        // Notify the bridge about eventual transport included
        onTransportInfo(peerJingleSession, answer);
    }

    /**
     * Callback called when we receive 'transport-info' from conference
     * participant. The info is forwarded to the videobridge at this point.
     *
     * {@inheritDoc}
     */
    @Override
    public void onTransportInfo(JingleSession session,
                                List<ContentPacketExtension> contentList)
    {
        Participant participant = findParticipantForJingleSession(session);
        if (participant == null)
        {
            logger.error("Failed to process transport-info," +
                             " no session for: " + session.getAddress());
            return;
        }

        if (participant.hasBundleSupport())
        {
            // Select first transport
            IceUdpTransportPacketExtension transport = null;
            for (ContentPacketExtension cpe : contentList)
            {
                IceUdpTransportPacketExtension contentTransport
                    = cpe.getFirstChildOfType(
                        IceUdpTransportPacketExtension.class);
                if (contentTransport != null)
                {
                    transport = contentTransport;
                    break;
                }
            }
            if (transport == null)
            {
                logger.error(
                    "No valid transport suppied in transport-update from "
                        + participant.getChatMember().getName());
                return;
            }

            transport.addChildExtension(
                new RtcpmuxPacketExtension());

            // FIXME: initiator
            boolean initiator = true;
            colibri.updateBundleTransportInfo(
                initiator,
                transport,
                participant.getColibriChannelsInfo());
        }
        else
        {
            Map<String, IceUdpTransportPacketExtension> transportMap
                = new HashMap<String, IceUdpTransportPacketExtension>();

            for (ContentPacketExtension cpe : contentList)
            {
                IceUdpTransportPacketExtension transport
                    = cpe.getFirstChildOfType(
                            IceUdpTransportPacketExtension.class);
                if (transport != null)
                {
                    transportMap.put(cpe.getName(), transport);
                }
            }

            // FIXME: initiator
            boolean initiator = true;
            colibri.updateTransportInfo(
                initiator,
                transportMap,
                participant.getColibriChannelsInfo());
        }
    }

    /**
     * Callback called when we receive 'source-add' notification from conference
     * participant. New SSRCs received are advertised to active participants.
     * If some participant does not have Jingle session established yet then
     * those SSRCs are scheduled for future update.
     *
     * {@inheritDoc}
     */
    @Override
    public void onAddSource(JingleSession jingleSession,
                            List<ContentPacketExtension> contents)
    {
        Participant participant = findParticipantForJingleSession(jingleSession);
        if (participant == null)
        {
            logger.error("Add-source: no peer state for "
                             + jingleSession.getAddress());
            return;
        }

        participant.addSSRCsFromContent(contents);

        participant.addSSRCGroupsFromContent(contents);

        MediaSSRCMap ssrcsToAdd
            = MediaSSRCMap.getSSRCsFromContent(contents);

        MediaSSRCGroupMap ssrcGroupsToAdd
            = MediaSSRCGroupMap.getSSRCGroupsForContents(contents);

        // Updates SSRC Groups on the bridge
        colibri.updateSsrcGroupsInfo(
            participant.getSSRCGroupsCopy(),
            participant.getColibriChannelsInfo());

        for (Participant peerToNotify : participants)
        {
            if (peerToNotify == participant)
                continue;

            JingleSession peerJingleSession = peerToNotify.getJingleSession();
            if (peerJingleSession == null)
            {
                logger.warn(
                    "Add source: no call for "
                        + peerToNotify.getChatMember().getContactAddress());

                peerToNotify.scheduleSSRCsToAdd(ssrcsToAdd);

                peerToNotify.scheduleSSRCGroupsToAdd(ssrcGroupsToAdd);

                continue;
            }

            jingle.sendAddSourceIQ(
                ssrcsToAdd, ssrcGroupsToAdd, peerJingleSession);
        }
    }

    /**
     * Callback called when we receive 'source-remove' notification from
     * conference participant. New SSRCs received are advertised to active
     * participants. If some participant does not have Jingle session
     * established yet then those SSRCs are scheduled for future update.
     *
     * {@inheritDoc}
     */
    @Override
    public void onRemoveSource(JingleSession sourceJingleSession,
                               List<ContentPacketExtension> contents)
    {
        MediaSSRCMap ssrcsToRemove
            = MediaSSRCMap.getSSRCsFromContent(contents);

        MediaSSRCGroupMap ssrcGroupsToRemove
            = MediaSSRCGroupMap.getSSRCGroupsForContents(contents);

        removeSSRCs(sourceJingleSession, ssrcsToRemove, ssrcGroupsToRemove);
    }

    /**
     * Removes SSRCs from the conference and notifies other participants.
     *
     * @param sourceJingleSession source Jingle session from which SSRCs are
     *                            being removed.
     * @param ssrcsToRemove the {@link MediaSSRCMap} of SSRCs to be removed from
     *                      the conference.
     */
    private void removeSSRCs(JingleSession sourceJingleSession,
                             MediaSSRCMap ssrcsToRemove,
                             MediaSSRCGroupMap ssrcGroupsToRemove)
    {
        Participant sourcePeer
            = findParticipantForJingleSession(sourceJingleSession);
        if (sourcePeer == null)
        {
            logger.error("Remove-source: no session for "
                             + sourceJingleSession.getAddress());
            return;
        }

        sourcePeer.removeSSRCs(ssrcsToRemove);

        sourcePeer.removeSSRCGroups(ssrcGroupsToRemove);

        // Updates SSRC Groups on the bridge
        colibri.updateSsrcGroupsInfo(
            ssrcGroupsToRemove,
            sourcePeer.getColibriChannelsInfo());

        logger.info("Remove SSRC " + sourceJingleSession.getAddress());

        for (Participant peer : participants)
        {
            if (peer == sourcePeer)
                continue;

            JingleSession jingleSessionToNotify = peer.getJingleSession();
            if (jingleSessionToNotify == null)
            {
                logger.warn(
                    "Remove source: no jingle session for "
                        + peer.getChatMember().getContactAddress());

                peer.scheduleSSRCsToRemove(ssrcsToRemove);

                peer.scheduleSSRCGroupsToRemove(ssrcGroupsToRemove);

                continue;
            }

            jingle.sendRemoveSourceIQ(
                    ssrcsToRemove, ssrcGroupsToRemove, jingleSessionToNotify);
        }
    }

    /**
     * Gathers the list of all SSRCs of given media type that exist in current
     * conference state.
     *
     * @param media the media type of SSRCs that are being returned.
     *
     * @return the list of all SSRCs of given media type that exist in current
     *         conference state.
     */
    private List<SourcePacketExtension> getAllSSRCs(String media)
    {
        List<SourcePacketExtension> mediaSSRCs
            = new ArrayList<SourcePacketExtension>();

        for (Participant peer : participants)
        {
            List<SourcePacketExtension> peerSSRC
                = peer.getSSRCS().getSSRCsForMedia(media);

            if (peerSSRC != null)
                mediaSSRCs.addAll(peerSSRC);
        }

        return mediaSSRCs;
    }

    /**
     * Gathers the list of all SSRC groups of given media type that exist in
     * current conference state.
     *
     * @param media the media type of SSRC groups that are being returned.
     *
     * @return the list of all SSRC groups of given media type that exist in
     *         current conference state.
     */
    private List<SourceGroupPacketExtension> getAllSSRCGroups(String media)
    {
        List<SourceGroupPacketExtension> ssrcGroups
            = new ArrayList<SourceGroupPacketExtension>();

        for (Participant peer : participants)
        {
            List<SSRCGroup> peerSSRCGroups
                = peer.getSSRCGroupsForMedia(media);

            for (SSRCGroup ssrcGroup : peerSSRCGroups)
            {
                try
                {
                    ssrcGroups.add(ssrcGroup.getExtensionCopy());
                }
                catch (Exception e)
                {
                    logger.error("Error copying source group extension");
                }
            }
        }

        return ssrcGroups;
    }

    /**
     * Returns the name of conference multi-user chat room.
     */
    public String getRoomName()
    {
        return roomName;
    }

    /**
     * Returns XMPP protocol provider of the focus account.
     */
    public ProtocolProviderService getXmppProvider()
    {
        return protocolProviderHandler.getProtocolProvider();
    }

    /**
     * Attempts to modify conference recording state.
     *
     * @param from JID of the participant that wants to modify recording state.
     * @param token recording security token that will be verified on modify
     *              attempt.
     * @param state the new recording state to set.
     * @param path output recording path(recorder implementation and deployment
     *             dependent).
     * @param to the received colibri packet destination.
     * @return new recording state(unchanged if modify attempt has failed).
     */
    public State modifyRecordingState(
            String from, String token, State state, String path, String to)
    {
        ChatRoomMember member = findMember(from);
        if (member == null)
        {
            logger.error("No member found for address: " + from);
            return State.OFF;
        }
        if (ChatRoomMemberRole.MODERATOR.compareTo(member.getRole()) < 0)
        {
            logger.info("Recording - request denied, not a moderator: " + from);
            return State.OFF;
        }

        Recorder recorder = getRecorder();
        if (recorder == null)
        {
            if(state.equals(State.OFF))
            {
                earlyRecordingState = null;
                return State.OFF;
            }

            // save for later dispatching
            earlyRecordingState = new RecordingState(
                from, token, state, path, to);
            return State.PENDING;
        }

        boolean isTokenCorrect
            = recorder.setRecording(from, token, state.equals(State.ON), path);
        if (!isTokenCorrect)
        {
            logger.info(
                "Incorrect recording token received ! Session: "
                    + chatRoom.getName());
        }

        return recorder.isRecording() ? State.ON : State.OFF;
    }

    private ChatRoomMember findMember(String from)
    {
        for (ChatRoomMember member : chatRoom.getMembers())
        {
            if (member.getContactAddress().equals(from))
            {
                return member;
            }
        }
        return null;
    }

    /**
     * Returns {@link System#currentTimeMillis()} timestamp indicating the time
     * when this conference has become idle(we can measure how long is it).
     * -1 is returned if this conference is considered active.
     *
     */
    public long getIdleTimestamp()
    {
        return idleTimestamp;
    }

    /**
     * Returns focus MUC JID if it is in the room or <tt>null</tt> otherwise.
     * JID example: room_name@muc.server.com/focus_nickname.
     */
    public String getFocusJid()
    {
        return chatRoom != null
            ? chatRoom.getName() + "/" + FOCUS_NICK
            : null;
    }

    /**
     * Returns {@link JitsiMeetServices} instance used in this conference.
     */
    public JitsiMeetServices getServices()
    {
        return services;
    }

    /**
     * Handles mute request sent from participants.
     * @param fromJid MUC jid of the participant that requested mute status
     *                change.
     * @param toBeMutedJid MUC jid of the participant whose mute status will be
     *                     changed(eventually).
     * @param doMute the new audio mute status to set.
     * @return <tt>true</tt> if status has been set successfully.
     */
    boolean handleMuteRequest(String fromJid,
                              String toBeMutedJid,
                              boolean doMute)
    {
        Participant principal = findParticipantForMucAddress(fromJid);
        if (principal == null)
        {
            logger.error(
                "Failed to perform mute operation - " + fromJid
                    +" not exists in the conference.");
            return false;
        }
        // Only moderators can mute others
        if (!fromJid.equals(toBeMutedJid)
            && ChatRoomMemberRole.MODERATOR.compareTo(
                principal.getChatMember().getRole()) < 0)
        {
            logger.error(
                "Permission denied for mute operation from " + fromJid);
            return false;
        }

        Participant participant = findParticipantForMucAddress(toBeMutedJid);
        if (participant == null)
        {
            logger.error("Participant for jid: " + toBeMutedJid + " not found");
            return false;
        }

        logger.info(
            "Will " + (doMute ? "mute" : "unmute")
                + " " + toBeMutedJid + " on behalf of " + fromJid);

        boolean succeeded = colibri.muteParticipant(
            participant.getColibriChannelsInfo(), doMute);

        if (succeeded)
        {
            participant.setMuted(doMute);
        }

        return succeeded;
    }

    /**
     * Called by {@link FocusManager} when the user identified by given
     * <tt>realJid</tt> gets confirmed <tt>identity</tt> by authentication
     * component.
     *
     * @param realJid the real user JID(not MUC JID which can be faked).
     * @param identity the identity of the user confirmed by authetication
     *                 component.
     */
    void userAuthenticated(String realJid, String identity)
    {
        // FIXME: consider changing to debug log level once tested
        logger.info("Authenticate request for: " + realJid + " as " + identity);

        Participant participant = findParticipantForJabberId(realJid);
        if (participant == null)
        {
            logger.error("Auth request - no member found for JID: " + realJid);
            return;
        }

        ChatRoomMember chatMember = participant.getChatMember();
        if (chatMember == null)
        {
            logger.error("No chat member for JID: " + realJid);
            return;
        }

        if (participant.getAuthenticatedIdentity() != null)
        {
            logger.error(realJid + " already authenticated");
            return;
        }

        // Sets authenticated ID
        participant.setAuthenticatedIdentity(identity);
        // Grants moderator rights
        chatRoom.grantModerator(chatMember.getName());
    }

    /**
     * The interface used to listen for conference events.
     */
    public interface ConferenceListener
    {
        /**
         * Event fired when conference has ended.
         * @param conference the conference instance that has ended.
         */
        void conferenceEnded(JitsiMeetConference conference);
    }

    /**
     * Handles <tt>message</tt> stanzas addressed to the focus JID for this
     * conference. Note that there are no filters, and we only depend on the
     * fact that each <tt>JitsiMeetConference</tt> has its own
     * <tt>XMPPConnection</tt>.
     *
     * Currently we only support XEP-0337 "log" messages with a limited set of
     * IDs and predefined format. Specifically, we always expect the text in the
     * message to be base64-encoded and, if the "delfated" tag is set,
     * compressed in the RFC1951 raw DEFLATE format.
     *
     * @author Boris Grozev
     */
    private class MessageListener
        implements PacketListener
    {
        /**
         * The <tt>LoggingService</tt> which will be used to log the events
         * (usually to an InfluxDB instance).
         */
        private LoggingService loggingService = null;

        /**
         * Whether {@link #loggingService} has been initialized or not.
         */
        private boolean loggingServiceSet = false;

        /**
         * The string which identifies the contents of a log message as containg
         * PeerConnection statistics.
         */
        private static final String LOG_ID_PC_STATS = "PeerConnectionStats";

        /**
         * Processes a packet. Looks for known extensions (XEP-0337 "log"
         * extensions) and handles them.
         * @param packet the packet to process.
         */
        @Override
        public void processPacket(Packet packet)
        {
            if (!(packet instanceof Message))
                return;

            Message message = (Message) packet;

            LogPacketExtension log = null;
            for (PacketExtension ext : message.getExtensions())
            {
                if (ext instanceof LogPacketExtension)
                {
                    log = (LogPacketExtension) ext;
                    break;
                }
            }

            if (log != null)
            {
                Participant participant
                     = findParticipantForRoomJid(message.getFrom());
                if (participant != null)
                {
                    handleLogRequest(log, participant);
                }
                else
                {
                    logger.info("Ignoring log request from unknown JID: "
                                            + message.getFrom());
                }
            }
        }

        /**
         * Handles a <tt>LogPacketExtension</tt> which represents a request
         * from a specific <tt>Participant</tt> to log a message.
         * @param log the <tt>LogPacketExtension</tt> to handle.
         * @param participant the <tt>Participant</tt> which sent the request.
         */
        private void handleLogRequest(
                LogPacketExtension log,
                Participant participant)
        {
            LoggingService loggingService = getLoggingService();
            if (loggingService != null)
            {
                if (LOG_ID_PC_STATS.equals(log.getID()))
                {
                    String content = getContent(log);
                    if (content != null)
                    {
                        loggingService.logEvent(
                                LogEventFactory.peerConnectionStats(
                                        colibri.getConferenceId(),
                                        participant.getChatMember().getName(),
                                        content));
                    }
                }
                else
                {
                    if (logger.isInfoEnabled())
                        logger.info("Ignoring log request with an unknown ID:"
                            + log.getID());
                }

            }
        }

        /**
         * Gets the <tt>LoggingService</tt>.
         * @return  the <tt>LoggingService</tt>.
         */
        private LoggingService getLoggingService()
        {
            if (!loggingServiceSet)
            {
                loggingServiceSet = true;
                loggingService = FocusBundleActivator.getLoggingService();
            }

            return loggingService;
        }

        /**
         * Extracts the message to be logged from a <tt>LogPacketExtension</tt>.
         * Takes care of base64 decoding and (optionally) decompression.
         * @param log the <tt>LogPacketExtension</tt> to handle.
         * @return the decoded message contained in <tt>log</tt>.
         */
        private String getContent(LogPacketExtension log)
        {
            String messageBase64 = log.getMessage();
            byte[] messageBytes = net.java.sip.communicator.util.Base64.decode(messageBase64);

            if (Boolean.parseBoolean(log.getTagValue("deflated")))
            {
                // nowrap=true, because we expect "raw" deflate
                Inflater inflater = new Inflater(true);
                ByteArrayOutputStream result = new ByteArrayOutputStream();

                inflater.setInput(messageBytes);
                byte[] buf = new byte[10000];

                do
                {
                    try
                    {
                        int len = inflater.inflate(buf);
                        result.write(buf, 0, len);
                    }
                    catch (DataFormatException dfe)
                    {
                        if (logger.isInfoEnabled())
                            logger.info(
                                "Failed to inflate log request content:" + dfe);
                        return null;
                    }
                } while (!inflater.finished());

                return result.toString();
            }
            else
            {
                return new String(messageBytes);
            }
        }
    }


    /**
     * Saves early recording requests by user. Dispatched when new participant
     * joins.
     */
    private class RecordingState
    {
        /**
         * JID of the participant that wants to modify recording state.
         */
        String from;

        /**
         * Recording security token that will be verified on modify attempt.
         */
        String token;

        /**
         * The new recording state to set.
         */
        State state;

        /**
         * Output recording path(recorder implementation
         * and deployment dependent).
         */
        String path;

        /**
         * The received colibri packet destination.
         */
        String to;

        public RecordingState(String from, String token,
            State state, String path, String to)
        {
            this.from = from;
            this.token = token;
            this.state = state;
            this.path = path;
            this.to = to;
        }
    }
}
