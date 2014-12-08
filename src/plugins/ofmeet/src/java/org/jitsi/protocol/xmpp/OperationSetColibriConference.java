/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.*;

import org.jitsi.jicofo.*;
import org.jitsi.protocol.*;

import java.util.*;

/**
 * Operation set exposes an interface for direct Colibri protocol communication
 * with the videobridge. Allows to allocate new channels, update transport info
 * and finally expire colibri channels.
 *
 * @author Pawel Domas
 */
public interface OperationSetColibriConference
    extends OperationSet
{
    /**
     * Sets Jitsi videobridge XMPP address to be used to allocate
     * the conferences.
     *
     * @param videobridgeJid the videobridge address to be set.
     */
    void setJitsiVideobridge(String videobridgeJid);

    /**
     * Returns XMPP address of curently used videobridge or <tt>null</tt>
     * if the isn't any.
     */
    String getJitsiVideobridge();

    /**
     * Sets conference configuration instance that will be used to adjust
     * Colibri channels properties.
     * @param config an instance of <tt>JitsiMeetConfig</tt> to be used by this
     *               Colibri operation set.
     */
    void setJitsiMeetConfig(JitsiMeetConfig config);

    /**
     * Returns the identifier assigned for our conference by the videobridge.
     * Will returns <tt>null</tt> if no conference has been allocated yet.
     */
    String getConferenceId();

    /**
     * Creates channels on the videobridge for given parameters.
     *
     * @param useBundle <tt>true</tt> if channel transport bundle should be used
     *                  for this allocation.
     * @param endpointName the name that will identify channels endpoint.
     * @param peerIsInitiator <tt>true</tt> if peer is ICE an initiator
     *                        of ICE session.
     * @param contents content list that describes peer media.
     *
     * @return <tt>ColibriConferenceIQ</tt> that describes allocated channels.
     *
     * @throws OperationFailedException if channel allocation failed due to
     *                                  network or bridge failure.
     */
    ColibriConferenceIQ createColibriChannels(
            boolean useBundle,
            String endpointName,
            boolean peerIsInitiator,
            List<ContentPacketExtension> contents)
            throws OperationFailedException;

    /**
     * Updates transport information for active channels
     * (existing on the bridge).
     *
     * @param initiator <tt>true</tt> if peer is the initiator of ICE session.
     * @param map the map of content name to transport packet extension.
     * @param localChannelsInfo <tt>ColibriConferenceIQ</tt> that contains
     *                          the description of the channel for which
     *                          transport information will be updated
     *                          on the bridge.
     */
    void updateTransportInfo(
            boolean initiator,
            Map<String, IceUdpTransportPacketExtension> map,
            ColibriConferenceIQ localChannelsInfo);

    /**
     * Updates simulcast layers on the bridge.
     * @param ssrcGroups the map of media SSRC groups that will be updated on
     *                   the bridge.
     * @param localChannelsInfo <<tt>ColibriConferenceIQ</tt> that contains
     *                          the description of the channel for which
     *                          SSRC groups information will be updated
     *                          on the bridge.</tt>
     */
    void updateSsrcGroupsInfo(
        MediaSSRCGroupMap ssrcGroups,
        ColibriConferenceIQ localChannelsInfo);

    /**
     * Updates channel bundle transport information for channels described by
     * <tt>localChannelsInfo</tt>. Single transport is set on the bundle shared
     * by all channels described by given IQ and only one bundle group can be
     * updated by single call to this method.
     *
     * @param initiator <tt>true</tt> if peer is the initiator of ICE session.
     * @param transport the transport packet extension that contains channel
     *                  bundle transport candidates.
     * @param localChannelsInfo <tt>ColibriConferenceIQ</tt> that contains
     *                          the description of the channels sharing the same
     *                          bundle group.
     */
    void updateBundleTransportInfo(
            boolean initiator,
            IceUdpTransportPacketExtension transport,
            ColibriConferenceIQ localChannelsInfo);

    /**
     * Expires the channels described by given <tt>ColibriConferenceIQ</tt>.
     *
     * @param channelInfo the <tt>ColibriConferenceIQ</tt> that contains
     *                    information about the channel to be expired.
     */
    void expireChannels(ColibriConferenceIQ channelInfo);

    /**
     * Expires all channels in current conference and resets conference state.
     */
    void expireConference();

    /**
     * Mutes audio channels described in given IQ by changing their media
     * direction to {@link org.jitsi.service.neomedia.MediaDirection#SENDONLY}.
     * @param channelsInfo the IQ that describes the channels to be muted.
     * @param mute <tt>true</tt> to mute or <tt>false</tt> to unmute audio
     *             channels described in <tt>channelsInfo</tt>.
     * @return <tt>true</tt> if the operation has succeeded or <tt>false</tt>
     *         otherwise.
     */
    boolean muteParticipant(ColibriConferenceIQ channelsInfo, boolean mute);
}
