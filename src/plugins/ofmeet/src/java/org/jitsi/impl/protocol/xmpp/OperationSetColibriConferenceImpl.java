/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.Logger;

import org.jitsi.jicofo.*;
import org.jitsi.protocol.*;
import org.jitsi.protocol.xmpp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.*;

import java.util.*;
import org.jivesoftware.util.JiveGlobals;

/**
 * Default implementation of {@link OperationSetColibriConference} that uses
 * Smack for handling XMPP connection. Handles conference state, allocates and
 * expires channels.
 *
 * @author Pawel Domas
 */
public class OperationSetColibriConferenceImpl
    implements OperationSetColibriConference
{
    private final static net.java.sip.communicator.util.Logger logger
        = Logger.getLogger(OperationSetColibriConferenceImpl.class);

    /**
     * The instance of XMPP connection.
     */
    private XmppConnection connection;

    /**
     * XMPP address of videobridge component.
     */
    private String jitsiVideobridge;

    /**
     * The {@link ColibriConferenceIQ} that stores the state of whole conference
     */
    private ColibriConferenceIQ conferenceState = new ColibriConferenceIQ();

    /**
     * Utility used for building Colibri queries.
     */
    private ColibriBuilder colibriBuilder
        = new ColibriBuilder(conferenceState);

    /**
     * Initializes this operation set.
     *
     * @param connection Smack XMPP connection impl that will be used to send
     *                   and receive XMPP packets.
     */
    public void initialize(XmppConnection connection)
    {
        this.connection = connection;

        // FIXME: Register Colibri
        ProviderManager.getInstance().addIQProvider(
            ColibriConferenceIQ.ELEMENT_NAME,
            ColibriConferenceIQ.NAMESPACE,
            new ColibriIQProvider());

        // FIXME: register Jingle
        ProviderManager.getInstance().addIQProvider(
            JingleIQ.ELEMENT_NAME,
            JingleIQ.NAMESPACE,
            new JingleIQProvider());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setJitsiVideobridge(String videobridgeJid)
    {
        if (!StringUtils.isNullOrEmpty(conferenceState.getID()))
        {
            throw new IllegalStateException(
                "Can not change the bridge on active conference");
        }
        this.jitsiVideobridge = videobridgeJid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getJitsiVideobridge()
    {
        return this.jitsiVideobridge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setJitsiMeetConfig(JitsiMeetConfig config)
    {
        colibriBuilder.setChannelLastN(config.getChannelLastN());
        colibriBuilder.setAdaptiveLastN(config.isAdaptiveLastNEnabled());
        colibriBuilder.setAdaptiveSimulcast(config.isAdaptiveSimulcastEnabled());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getConferenceId()
    {
        return conferenceState.getID();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ColibriConferenceIQ createColibriChannels(
            boolean useBundle,
            String endpointName,
            boolean peerIsInitiator,
            List<ContentPacketExtension> contents)
        throws OperationFailedException
    {
        colibriBuilder.reset();

        colibriBuilder.addAllocateChannelsReq(
            useBundle, endpointName, peerIsInitiator, contents);

        ColibriConferenceIQ allocateRequest
            = colibriBuilder.getRequest(jitsiVideobridge);

		ColibriConferenceIQ.Content colibriContent = allocateRequest.getContent("audio");

		boolean useAudioMixer = false;
		String useAudioString = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.audio.mixer");	// BAO
		if (useAudioString != null) useAudioMixer = useAudioString.equals("true");

		if (useAudioMixer  && colibriContent != null)
		{
			logger.info("audio mixer set " + colibriContent);

			for (ColibriConferenceIQ.Channel channel : colibriContent.getChannels())
			{
				channel.setRTPLevelRelayType(RTPLevelRelayType.MIXER);		// BAO
			}
		}

        //FIXME: retry allocation on timeout
        Packet response = connection.sendPacketAndGetReply(allocateRequest);

        if (response == null)
        {
            throw new OperationFailedException(
                "Failed to allocate colibri channels: response is null."
                + " Maybe the response timed out.",
                OperationFailedException.NETWORK_FAILURE);
        }
        else if (response.getError() != null)
        {
            throw new OperationFailedException(
                "Failed to allocate colibri channels: "
                    + response.getError(),
                OperationFailedException.GENERAL_ERROR);
        }
        else if (!(response instanceof ColibriConferenceIQ))
        {
            throw new OperationFailedException(
                "Failed to allocate colibri channels: response is not a"
                    + " colibri conference",
                OperationFailedException.GENERAL_ERROR);
        }

        /*
         * Update the complete ColibriConferenceIQ representation maintained by
         * this instance with the information given by the (current) response.
         */
        ColibriAnalyser analyser = new ColibriAnalyser(conferenceState);

        analyser.processChannelAllocResp((ColibriConferenceIQ) response);

        /*
         * Formulate the result to be returned to the caller which is a subset
         * of the whole conference information kept by this CallJabberImpl and
         * includes the remote channels explicitly requested by the method
         * caller and their respective local channels.
         */
        return ColibriAnalyser.getResponseContents(
                    (ColibriConferenceIQ) response, contents);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expireChannels(ColibriConferenceIQ channelInfo)
    {
        colibriBuilder.reset();

        colibriBuilder.addExpireChannelsReq(channelInfo);

        ColibriConferenceIQ iq = colibriBuilder.getRequest(jitsiVideobridge);
        if (iq != null)
        {
            connection.sendPacket(iq);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateTransportInfo(
            boolean initiator,
            Map<String, IceUdpTransportPacketExtension> map,
            ColibriConferenceIQ localChannelsInfo)
    {
        colibriBuilder.reset();

        colibriBuilder.addTransportUpdateReq(
            initiator, map, localChannelsInfo);

        ColibriConferenceIQ conferenceRequest
            = colibriBuilder.getRequest(jitsiVideobridge);

        if (conferenceRequest != null)
        {
            connection.sendPacket(conferenceRequest);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateSsrcGroupsInfo(MediaSSRCGroupMap ssrcGroups,
                                     ColibriConferenceIQ localChannelsInfo)
    {
        // FIXME: move to ColibriBuilder
        ColibriConferenceIQ updateIq = new ColibriConferenceIQ();

        updateIq.setID(conferenceState.getID());
        updateIq.setType(IQ.Type.SET);
        updateIq.setTo(jitsiVideobridge);

        boolean updateNeeded = false;

        for (ColibriConferenceIQ.Content content
            : localChannelsInfo.getContents())
        {
            String contentName = content.getName();
            if ("video".compareToIgnoreCase(contentName) != 0)
            {
                // Simulcast currently used for video only
                continue;
            }

            ColibriConferenceIQ.Content reqContent
                = new ColibriConferenceIQ.Content(content.getName());

            boolean hasChannels = false;
            for (ColibriConferenceIQ.Channel channel : content.getChannels())
            {
                ColibriConferenceIQ.Channel reqChannel
                    = new ColibriConferenceIQ.Channel();

                reqChannel.setID(channel.getID());

                List<SSRCGroup> groups
                    = ssrcGroups.getSSRCGroupsForMedia(content.getName());
                for (SSRCGroup group : groups)
                {
                    try
                    {
                        reqChannel.addSourceGroup(group.getExtensionCopy());
                        hasChannels = true;
                        updateNeeded = true;
                    }
                    catch (Exception e)
                    {
                        logger.error("Error copying extension", e);
                    }
                }
                if (groups.isEmpty())
                {
                    // Put empty source group to turn off simulcast layers
                    reqChannel.addSourceGroup(
                        SourceGroupPacketExtension.createSimulcastGroup());
                    hasChannels = true;
                    updateNeeded = true;
                }
                reqContent.addChannel(reqChannel);
            }
            if (hasChannels)
            {
                updateIq.addContent(reqContent);
            }
        }

        if (updateNeeded)
        {
            connection.sendPacketAndGetReply(updateIq);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateBundleTransportInfo(
            boolean                        initiator,
            IceUdpTransportPacketExtension transport,
            ColibriConferenceIQ            localChannelsInfo)
    {
        colibriBuilder.reset();

        colibriBuilder.addBundleTransportUpdateReq(
            initiator, transport, localChannelsInfo);

        ColibriConferenceIQ conferenceRequest
            = colibriBuilder.getRequest(jitsiVideobridge);

        if (conferenceRequest != null)
        {
            connection.sendPacket(conferenceRequest);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expireConference()
    {
        colibriBuilder.reset();

        if (StringUtils.isNullOrEmpty(conferenceState.getID()))
        {
            logger.info("Nothing to expire - no conference allocated yet");
            return;
        }

        // Expire all channels
        colibriBuilder.addExpireChannelsReq(conferenceState);

        ColibriConferenceIQ colibriRequest
            = colibriBuilder.getRequest(jitsiVideobridge);

        if (colibriRequest != null)
        {
            connection.sendPacket(colibriRequest);
        }

        // Reset conference state
        conferenceState = new ColibriConferenceIQ();
    }

    @Override
    public boolean muteParticipant(ColibriConferenceIQ channelsInfo, boolean mute)
    {
        ColibriConferenceIQ request = new ColibriConferenceIQ();
        request.setID(conferenceState.getID());

        ColibriConferenceIQ.Content audioContent
            = channelsInfo.getContent("audio");
        if (audioContent == null)
        {
            logger.error("Failed to mute - no audio content." +
                             " Conf ID: " + request.getID());
            return false;
        }
        ColibriConferenceIQ.Content contentRequest
            = new ColibriConferenceIQ.Content(audioContent.getName());

        for (ColibriConferenceIQ.Channel channel : audioContent.getChannels())
        {
            ColibriConferenceIQ.Channel channelRequest
                = new ColibriConferenceIQ.Channel();

            channelRequest.setID(channel.getID());

            if (mute)
            {
                channelRequest.setDirection(MediaDirection.SENDONLY);
            }
            else
            {
                channelRequest.setDirection(MediaDirection.SENDRECV);
            }

            contentRequest.addChannel(channelRequest);
        }

        if (contentRequest.getChannelCount() == 0)
        {
            logger.error("Failed to mute - no channels to modify." +
                             " ConfID:" + request.getID());
            return false;
        }

        request.setType(IQ.Type.SET);
        request.setTo(jitsiVideobridge);

        request.addContent(contentRequest);

        connection.sendPacket(request);

        // FIXME wait for response and set local status

        return true;
    }
}
