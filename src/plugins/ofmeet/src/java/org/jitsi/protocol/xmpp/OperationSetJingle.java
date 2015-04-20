/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.*;

import org.jitsi.jicofo.*;
import org.jitsi.protocol.xmpp.util.*;

import java.util.*;

/**
 * Operation set allows to establish and control Jingle sessions. Exposed
 * functionality is limited to the minimum required by Jitsi Meet conference.
 * {@link org.jitsi.protocol.xmpp.JingleRequestHandler} set using
 * {@link #setRequestHandler(JingleRequestHandler)} will receive notifications
 * about various Jingle packets received.
 *
 * @author Pawel Domas
 */
public interface OperationSetJingle
    extends OperationSet
{
    /**
     * Start new session by sending 'session-initiate' IQ to given XMPP address.
     *
     * @param useBundle <tt>true</tt> if contents description in the IQ sent
     *                  should contain additional signaling required for RTP
     *                  bundle usage in Jitsi Meet.
     * @param address the XMPP address that will be remote destination of new
     *                Jingle session.
     * @param contents media contents description of our offer.
     */
    void initiateSession(
            boolean useBundle,
            String address,
            List<ContentPacketExtension> contents);

    /**
     * Sends 'source-add' proprietary notification.
     *
     * @param ssrcMap the media SSRCs map which will be included in
     *                the notification.
     * @param ssrcGroupMap the map of media SSRC groups that will be included in
     *                     the notification.
     * @param session the <tt>JingleSession</tt> used to send the notification.
     */
    void sendAddSourceIQ(MediaSSRCMap ssrcMap,
                         MediaSSRCGroupMap ssrcGroupMap,
                         JingleSession session);

    /**
     * Sends 'source-remove' notification to the peer of given
     * <tt>JingleSession</tt>.
     *
     * @param ssrcMap the map of media SSRCs that will be included in
     *                the notification.
     * @param ssrcGroupMap the map of media SSRC groups that will be included in
     *                     the notification.
     * @param session the <tt>JingleSession</tt> used to send the notification.
     */
    void sendRemoveSourceIQ(MediaSSRCMap ssrcMap,
                            MediaSSRCGroupMap ssrcGroupMap,
                            JingleSession session);

    /**
     * Sets the {@link JingleRequestHandler} that will receive jingle
     * notifications.
     *
     * @param jingleRequestHandler {@link JingleRequestHandler} object that will
     *        be receiving jingle notifications from this operation set.
     */
    void setRequestHandler(JingleRequestHandler jingleRequestHandler);

    /**
     * Terminates given session by sending 'session-terminate' IQ which will
     * optionally include the <tt>Reason</tt> supplied.
     *
     * @param session the <tt>JingleSession</tt> to be terminated.
     * @param reason optional <tt>Reason</tt> specifying the reason of session
     *               termination.
     */
    void terminateSession(JingleSession session, Reason reason);

}
