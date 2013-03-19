package net.sf.kraken.util.chatstate;

import java.util.EventObject;

import net.jcip.annotations.Immutable;
import net.sf.kraken.type.ChatStateType;

import org.xmpp.packet.JID;

/**
 * An event that indicates that a chat state status has changed.
 * 
 * Instances of this class are immutable.
 * 
 * @author Guus der Kinderen
 * @see <a
 *      href="http://xmpp.org/extensions/xep-0085.html">XEP-0085:&nbsp;Chat&nbsp;State&nbsp;Notifications</a>
 */
@Immutable
public class ChatStateChangeEvent extends EventObject {

    /**
     * The entity that originates the chat state notification.
     */
    public final JID sender;

    /**
     * The entity that is the recipient of the chat state notification.
     */
    public final JID receiver;

    /**
     * The type of chat state notification to which is being changed.
     */
    public final ChatStateType type;

    /**
     * Instantiates a new event that signals that a particular entity changed
     * its state of a chat in a particular conversation with another entity.
     * 
     * @param sender
     *            The entity that originates the chat state notification.
     * @param receiver
     *            The entity that is the recipient of the chat state
     *            notification.
     * @param type
     *            The type of chat state notification to which is being changed.
     */
    public ChatStateChangeEvent(JID sender, JID receiver, ChatStateType type) {
        super(sender);
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
    }

    /**
     * The entity that originates the chat state notification.
     * 
     * @return the entity that originates the chat state notification.
     */
    public JID getSender() {
        return sender;
    }

    /**
     * The entity that is the recipient of the chat state notification.
     * 
     * @return the entity that is the recipient of the chat state notification.
     */
    public JID getReceiver() {
        return receiver;
    }

    /**
     * The type of chat state notification to which is being changed.
     * 
     * @return The type of chat state notification to which is being changed.
     */
    public ChatStateType getType() {
        return type;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ChatStateEvent [receiver=");
        builder.append(receiver);
        builder.append(", sender=");
        builder.append(sender);
        builder.append(", type=");
        builder.append(type);
        builder.append("]");
        return builder.toString();
    }
}
