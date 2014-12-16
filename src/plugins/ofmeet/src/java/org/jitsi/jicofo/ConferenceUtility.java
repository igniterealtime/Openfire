/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.service.protocol.*;

import org.jitsi.protocol.xmpp.*;

/**
 * Class adds utility methods that require use of package protected methods of
 * {@link JitsiMeetConference}. These are used only for test purposes, so are
 * placed in separate class to reduce size of the conference focus class.
 *
 * @author Pawel Domas
 */
public class ConferenceUtility
{
    /**
     * Conference instance.
     */
    private final JitsiMeetConference conference;

    /**
     * Creates new instance for given <tt>JitsiMeetConference</tt>.
     * @param conference the conference that wil be used by this instance.
     */
    public ConferenceUtility(JitsiMeetConference conference)
    {
        this.conference = conference;
    }

    /**
     * Returns the ID of Colibri conference hosted on the videobridge.
     */
    public String getJvbConferenceId()
    {
        ProtocolProviderService pps = conference.getXmppProvider();
        OperationSetColibriConference colibri
            = pps.getOperationSet(OperationSetColibriConference.class);
        return colibri.getConferenceId();
    }

    /**
     * Returns the id of video channel allocated for the participant with given
     * JID.
     * @param participantJid the MUC JID of the participant for whom we want to
     *                       get video channel id.
     */
    public String getParticipantVideoChannelId(String participantJid)
    {
        Participant participant
            = conference.findParticipantForRoomJid(participantJid);

        ColibriConferenceIQ channelsInfo
            = participant.getColibriChannelsInfo();

        ColibriConferenceIQ.Content videoContent
            = channelsInfo.getContent("video");

        ColibriConferenceIQ.Channel videoChannel
            = videoContent.getChannel(0);

        return videoChannel.getID();
    }
}
