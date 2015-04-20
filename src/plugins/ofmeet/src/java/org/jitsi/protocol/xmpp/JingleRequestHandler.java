/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import java.util.*;

/**
 * Listener class notified about Jingle requests received during the session.
 *
 * @author Pawel Domas
 */
public interface JingleRequestHandler
{
    /**
     * Callback fired when 'source-add' proprietary Jingle notification is
     * received.
     *
     * @param jingleSession the session that has received the notification.
     * @param contents contents list that describe media SSRCs. We expect
     *                 to find {@link net.java.sip.communicator.impl.protocol
     *                 .jabber.extensions.colibri.SourcePacketExtension} inside
     *                 of <tt>RtpDescriptionPacketExtension</tt> or in the
     *                 <tt>ContentPacketExtension</tt> directly.
     */
    void onAddSource(JingleSession jingleSession,
                     List<ContentPacketExtension> contents);

    /**
     * Callback fired when 'source-remove' proprietary Jingle notification is
     * received.
     *
     * @param jingleSession the session that has received the notification.
     * @param contents contents list that describe media SSRCs. We expect
     *                 to find {@link net.java.sip.communicator.impl.protocol
     *                 .jabber.extensions.colibri.SourcePacketExtension} inside
     *                 of <tt>RtpDescriptionPacketExtension</tt> or in the
     *                 <tt>ContentPacketExtension</tt> directly.
     */
    void onRemoveSource(JingleSession jingleSession,
                        List<ContentPacketExtension> contents);

    /**
     * Callback fired when 'session-accept' is received from the client.
     *
     * @param jingleSession the session that has received the notification.
     * @param answer content list that describe peer media offer.
     */
    void onSessionAccept(JingleSession jingleSession,
                         List<ContentPacketExtension> answer);

    /**
     * Callback fired when 'transport-info' is received from the client.
     *
     * @param jingleSession the session that has received the notification.
     * @param contents content list that contains media transport description.
     */
    void onTransportInfo(JingleSession jingleSession,
                         List<ContentPacketExtension> contents);

}
