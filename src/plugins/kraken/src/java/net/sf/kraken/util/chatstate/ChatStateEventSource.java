/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.util.chatstate;

import net.jcip.annotations.ThreadSafe;
import net.sf.kraken.type.ChatStateType;

import org.xmpp.packet.JID;

/**
 * An extension of {@link AbstractChatStateUtil} that will generate
 * {@link ChatStateChangeEvent}s.
 * 
 * @author Guus der Kinderen
 * @see <a
 *      href="http://xmpp.org/extensions/xep-0085.html">XEP-0085:&nbsp;Chat&nbsp;State&nbsp;Notifications</a>
 */
@ThreadSafe
public class ChatStateEventSource extends AbstractChatStateUtil {

    /**
     * The event listener that will receive change events.
     */
    public final ChatStateEventListener listener;

    /**
     * Constructs a new instance that will report any changes to the provided
     * event listener.
     * 
     * @param listener
     *            The event listener that will receive change events.
     */
    public ChatStateEventSource(final ChatStateEventListener listener) {
        this.listener = listener;
    }

    /* (non-Javadoc)
     * @see net.sf.kraken.util.chatstate.AbstractChatStateUtil#sendIsActive(org.xmpp.packet.JID, org.xmpp.packet.JID)
     */
    @Override
    public void sendIsActive(JID sender, JID receiver) {
        final ChatStateChangeEvent event = new ChatStateChangeEvent(sender, receiver, ChatStateType.active);
        listener.chatStateChange(event);
    }

    /* (non-Javadoc)
     * @see net.sf.kraken.util.chatstate.AbstractChatStateUtil#sendIsComposing(org.xmpp.packet.JID, org.xmpp.packet.JID)
     */
    @Override
    public void sendIsComposing(JID sender, JID receiver) {
        final ChatStateChangeEvent event = new ChatStateChangeEvent(sender, receiver, ChatStateType.composing);
        listener.chatStateChange(event);
    }

    /* (non-Javadoc)
     * @see net.sf.kraken.util.chatstate.AbstractChatStateUtil#sendIsGone(org.xmpp.packet.JID, org.xmpp.packet.JID)
     */
    @Override
    public void sendIsGone(JID sender, JID receiver) {
        final ChatStateChangeEvent event = new ChatStateChangeEvent(sender, receiver, ChatStateType.gone);
        listener.chatStateChange(event);
    }

    /* (non-Javadoc)
     * @see net.sf.kraken.util.chatstate.AbstractChatStateUtil#sendIsInactive(org.xmpp.packet.JID, org.xmpp.packet.JID)
     */
    @Override
    public void sendIsInactive(JID sender, JID receiver) {
        final ChatStateChangeEvent event = new ChatStateChangeEvent(sender, receiver, ChatStateType.inactive);
        listener.chatStateChange(event);
    }

    /* (non-Javadoc)
     * @see net.sf.kraken.util.chatstate.AbstractChatStateUtil#sendIsPaused(org.xmpp.packet.JID, org.xmpp.packet.JID)
     */
    @Override
    public void sendIsPaused(JID sender, JID receiver) {
        final ChatStateChangeEvent event = new ChatStateChangeEvent(sender, receiver, ChatStateType.paused);
        listener.chatStateChange(event);
    }
}
