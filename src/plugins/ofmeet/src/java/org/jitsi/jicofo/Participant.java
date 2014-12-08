/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import org.jitsi.protocol.xmpp.*;
import org.jitsi.protocol.xmpp.util.*;

import java.util.*;

/**
 * Class represent Jitsi Meet conference participant. Stores information about
 * Colibri channels allocated, Jingle session and media SSRCs.
 *
 * @author Pawel Domas
 */
public class Participant
{
    /**
     * MUC chat member of this participant.
     */
    private final XmppChatMember roomMember;

    /**
     * Jingle session(if any) established with this peer.
     */
    private JingleSession jingleSession;

    /**
     * Information about Colibri channels allocated for this peer(if any).
     */
    private ColibriConferenceIQ colibriChannelsInfo;

    /**
     * Peer's media SSRCs.
     */
    private MediaSSRCMap ssrcs = new MediaSSRCMap();

    /**
     * Peer's media SSRC groups.
     */
    private MediaSSRCGroupMap ssrcGroups = new MediaSSRCGroupMap();

    /**
     * SSRCs received from other peers scheduled for later addition, because
     * of the Jingle session not being ready at the point when SSRCs appeared in
     * the conference.
     */
    private MediaSSRCMap ssrcsToAdd = new MediaSSRCMap();

    /**
     * SSRC groups received from other peers scheduled for later addition.
     * @see #ssrcsToAdd
     */
    private MediaSSRCGroupMap ssrcGroupsToAdd = new MediaSSRCGroupMap();

    /**
     * SSRCs received from other peers scheduled for later removal, because
     * of the Jingle session not being ready at the point when SSRCs appeared in
     * the conference.
     * FIXME: do we need that since these were never added ? - check
     */
    private MediaSSRCMap ssrcsToRemove = new MediaSSRCMap();

    /**
     * SSRC groups received from other peers scheduled for later removal.
     * @see #ssrcsToRemove
     */
    private MediaSSRCGroupMap ssrcGroupsToRemove = new MediaSSRCGroupMap();

    /**
     * Indicates whether this peer has RTP bundle and RTCP-mux support.
     */
    private boolean hasBundleSupport;

    /**
     * Flag used to mark SIP gateway participants.
     */
    private boolean isSipGateway;

    /**
     * Remembers participant's muted status.
     */
    private boolean mutedStatus;

    /**
     * Participant's identity confirmed by authentication component.
     */
    private String authenticatedIdentity;

    /**
     * Creates new {@link Participant} for given chat room member.
     *
     * @param roomMember the {@link XmppChatMember} that represent this
     *                   participant in MUC conference room.
     */
    public Participant(XmppChatMember roomMember)
    {
        this.roomMember = roomMember;
    }

    /**
     * Returns {@link JingleSession} established with this conference
     * participant or <tt>null</tt> if there is no session yet.
     */
    public JingleSession getJingleSession()
    {
        return jingleSession;
    }

    /**
     * Sets {@link JingleSession} established with this peer.
     * @param jingleSession the new Jingle session to be assigned to this peer.
     */
    public void setJingleSession(JingleSession jingleSession)
    {
        this.jingleSession = jingleSession;
    }

    /**
     * Returns {@link XmppChatMember} that represents this participant in
     * conference multi-user chat room.
     */
    public XmppChatMember getChatMember()
    {
        return roomMember;
    }

    /**
     * Imports media SSRCs from given list of <tt>ContentPacketExtension</tt>.
     * @param answer the list that contains peer's media contents.
     */
    public void addSSRCsFromContent(List<ContentPacketExtension> answer)
    {
        ssrcs.add(MediaSSRCMap.getSSRCsFromContent(answer));
    }

    /**
     * Removes given media SSRCs from this peer state.
     * @param ssrcMap the SSRC map that contains the SSRCs to be removed.
     */
    public void removeSSRCs(MediaSSRCMap ssrcMap)
    {
        this.ssrcs.remove(ssrcMap);
    }

    /**
     * Returns the {@link MediaSSRCMap} which contains this peer's media SSRCs.
     */
    public MediaSSRCMap getSSRCS()
    {
        return ssrcs;
    }

    /**
     * Returns shallow copy of this peer's media SSRC map.
     */
    public MediaSSRCMap getSSRCsCopy()
    {
        return ssrcs.copyShallow();
    }

    /**
     * Returns deep copy of this peer's media SSRC group map.
     */
    public MediaSSRCGroupMap getSSRCGroupsCopy()
    {
        return ssrcGroups.copy();
    }

    /**
     * Returns <tt>true</tt> if this peer has any not synchronized SSRCs
     * scheduled for addition.
     */
    public boolean hasSsrcsToAdd()
    {
        return !ssrcsToAdd.isEmpty() || !ssrcGroupsToAdd.isEmpty();
    }

    /**
     * Reset the queue that holds not synchronized SSRCs scheduled for future
     * addition.
     */
    public void clearSsrcsToAdd()
    {
        ssrcsToAdd = new MediaSSRCMap();
        ssrcGroupsToAdd = new MediaSSRCGroupMap();
    }

    /**
     * Reset the queue that holds not synchronized SSRCs scheduled for future
     * removal.
     */
    public void clearSsrcsToRemove()
    {
        ssrcsToRemove = new MediaSSRCMap();
        ssrcGroupsToRemove = new MediaSSRCGroupMap();
    }

    /**
     * Returns <tt>true</tt> if this peer has any not synchronized SSRCs
     * scheduled for removal.
     */
    public boolean hasSsrcsToRemove()
    {
        return !ssrcsToRemove.isEmpty() || !ssrcGroupsToRemove.isEmpty();
    }

    /**
     * Returns <tt>true</tt> if this peer has any not synchronized SSRCs
     * scheduled for addition.
     */
    public MediaSSRCMap getSsrcsToAdd()
    {
        return ssrcsToAdd;
    }

    /**
     * Returns <tt>true</tt> if this peer has any not synchronized SSRCs
     * scheduled for removal.
     */
    public MediaSSRCMap getSsrcsToRemove()
    {
        return ssrcsToRemove;
    }

    /**
     * Schedules SSRCs received from other peer for future 'source-add' update.
     *
     * @param ssrcMap the media SSRC map that contains SSRCs for future updates.
     */
    public void scheduleSSRCsToAdd(MediaSSRCMap ssrcMap)
    {
        ssrcsToAdd.add(ssrcMap);
    }

    /**
     * Schedules SSRCs received from other peer for future 'source-remove'
     * update.
     *
     * @param ssrcMap the media SSRC map that contains SSRCs for future updates.
     */
    public void scheduleSSRCsToRemove(MediaSSRCMap ssrcMap)
    {
        ssrcsToRemove.add(ssrcMap);
    }

    /**
     * Sets information about Colibri channels allocated for this participant.
     *
     * @param colibriChannelsInfo the IQ that holds colibri channels state.
     */
    public void setColibriChannelsInfo(ColibriConferenceIQ colibriChannelsInfo)
    {
        this.colibriChannelsInfo = colibriChannelsInfo;
    }

    /**
     * Returns {@link ColibriConferenceIQ} that describes Colibri channels
     * allocated for this participant.
     */
    public ColibriConferenceIQ getColibriChannelsInfo()
    {
        return colibriChannelsInfo;
    }

    /**
     * Sets RTP bundle support on this participant.
     *
     * @param hasBundleSupport <tt>true</tt> if this participant has RTP bundle
     *                         support.
     */
    public void setHasBundleSupport(boolean hasBundleSupport)
    {
        this.hasBundleSupport = hasBundleSupport;
    }

    /**
     * Returns <tt>true</tt> if this participant supports RTP bundle and RTCP
     * mux.
     */
    public boolean hasBundleSupport()
    {
        return hasBundleSupport;
    }

    /**
     * Returns <tt>true</tt> if this participant belongs to SIP gateway service.
     */
    public boolean isSipGateway()
    {
        return isSipGateway;
    }

    /**
     * Marks this participants as SIP gateway one.
     *
     * @param isSipGateway <tt>true</tt> if this participants belongs to
     *        the SIP gateway service.
     */
    public void setIsSipGateway(boolean isSipGateway)
    {
        this.isSipGateway = isSipGateway;
    }

    /**
     * Sets muted status of this participant.
     * @param mutedStatus new muted status to set.
     */
    public void setMuted(boolean mutedStatus)
    {
        this.mutedStatus = mutedStatus;
    }

    /**
     * Returns <tt>true</tt> if this participant is muted or <tt>false</tt>
     * otherwise.
     */
    public boolean isMuted()
    {
        return mutedStatus;
    }

    /**
     * Sets the identity confirmed by authentication component of this
     * participant.
     *
     * @param authenticatedIdentity the authenticated identity string to set.
     */
    public void setAuthenticatedIdentity(String authenticatedIdentity)
    {
        this.authenticatedIdentity = authenticatedIdentity;
    }

    /**
     * Returns a string with the identity of this participant which has been
     * confirmed by trusted authentication component.
     */
    public String getAuthenticatedIdentity()
    {
        return authenticatedIdentity;
    }

    /**
     * Returns the list of SSRC groups of given media type that belong ot this
     * participant.
     * @param media the name of media type("audio","video", ...)
     * @return the list of {@link SSRCGroup} for given media type.
     */
    public List<SSRCGroup> getSSRCGroupsForMedia(String media)
    {
        return ssrcGroups.getSSRCGroupsForMedia(media);
    }

    /**
     * Returns <tt>MediaSSRCGroupMap</tt> that contains the mapping of media
     * SSRC groups that describe media of this participant.
     */
    public MediaSSRCGroupMap getSSRCGroups()
    {
        return ssrcGroups;
    }

    /**
     * Adds SSRC groups for media described in given Jiongle content list.
     * @param contents the list of <tt>ContentPacketExtension</tt> that
     *                 describes media SSRC groups.
     */
    public void addSSRCGroupsFromContent(List<ContentPacketExtension> contents)
    {
        for (ContentPacketExtension content : contents)
        {
            List<SSRCGroup> groups
                = SSRCGroup.getSSRCGroupsForContent(content);

            ssrcGroups.addSSRCGroups(content.getName(), groups);
        }
    }

    /**
     * Schedules given media SSRC groups for later addition.
     * @param ssrcGroups the <tt>MediaSSRCGroupMap</tt> to be scheduled for
     *                   later addition.
     */
    public void scheduleSSRCGroupsToAdd(MediaSSRCGroupMap ssrcGroups)
    {
        ssrcGroupsToAdd.add(ssrcGroups);
    }

    /**
     * Schedules given media SSRC groups for later removal.
     * @param ssrcGroups the <tt>MediaSSRCGroupMap</tt> to be scheduled for
     *                   later removal.
     */
    public void scheduleSSRCGroupsToRemove(MediaSSRCGroupMap ssrcGroups)
    {
        ssrcGroupsToRemove.add(ssrcGroups);
    }

    /**
     * Returns the map of SSRC groups that are waiting for synchronization.
     */
    public MediaSSRCGroupMap getSSRCGroupsToAdd()
    {
        return ssrcGroupsToAdd;
    }

    /**
     * Returns the map of SSRC groups that are waiting for being removed from
     * peer session.
     */
    public MediaSSRCGroupMap getSsrcGroupsToRemove()
    {
        return ssrcGroupsToRemove;
    }

    /**
     * Removes SSRC groups from this participant state.
     * @param ssrcGroupsToRemove the map of SSRC groups that will be removed
     *                           from this participant media state description.
     */
    public void removeSSRCGroups(MediaSSRCGroupMap ssrcGroupsToRemove)
    {
        this.ssrcGroups.remove(ssrcGroupsToRemove);
    }
}
