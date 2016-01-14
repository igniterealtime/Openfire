package org.jivesoftware.openfire;

import org.junit.Test;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketExtension;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This tests the business rules for storing messages as described in <a href="http://xmpp.org/extensions/xep-0160.html#types">3. Handling of Message Types</a>.
 *
 * @author csh
 */
public class OfflineMessageStoreTest {

    @Test
    public void shouldNotStoreGroupChatMessages() {
        // XEP-0160: "groupchat" message types SHOULD NOT be stored offline
        Message message = new Message();
        message.setType(Message.Type.groupchat);
        assertFalse(OfflineMessageStore.shouldStoreMessage(message));
    }

    @Test
    public void shouldNotStoreHeadlineMessages() {
        // XEP-0160: "headline" message types SHOULD NOT be stored offline
        Message message = new Message();
        message.setType(Message.Type.headline);
        assertFalse(OfflineMessageStore.shouldStoreMessage(message));
    }

    @Test
    public void shouldNotStoreErrorMessages() {
        // XEP-0160: "error" message types SHOULD NOT be stored offline,
        Message message = new Message();
        message.setType(Message.Type.error);
        assertFalse(OfflineMessageStore.shouldStoreMessage(message));
    }

    @Test
    public void shouldStoreNormalMessages() {
        // XEP-0160: Messages with a 'type' attribute whose value is "normal" (or messages with no 'type' attribute) SHOULD be stored offline.
        Message message = new Message();
        message.setType(Message.Type.normal);
        assertTrue(OfflineMessageStore.shouldStoreMessage(message));

        Message message2 = new Message();
        assertTrue(OfflineMessageStore.shouldStoreMessage(message2));
    }

    @Test
    public void shouldNotStoreEmptyChatMessages() {
        // XEP-0160: "chat" message types SHOULD be stored offline unless they only contain chat state notifications
        Message message = new Message();
        message.setType(Message.Type.chat);
        assertFalse(OfflineMessageStore.shouldStoreMessage(message));
    }

    @Test
    public void shouldStoreNonEmptyChatMessages() {
        // XEP-0160: "chat" message types SHOULD be stored offline unless they only contain chat state notifications
        Message message = new Message();
        message.setType(Message.Type.chat);
        message.setBody(" ");
        assertTrue(OfflineMessageStore.shouldStoreMessage(message));
    }

    @Test
    public void shouldNotStoreEmptyChatMessagesWithOnlyChatStates() {
        Message message = new Message();
        message.setType(Message.Type.chat);
        PacketExtension chatState = new PacketExtension("composing", "http://jabber.org/protocol/chatstates");
        message.addExtension(chatState);
        assertFalse(OfflineMessageStore.shouldStoreMessage(message));
    }

    @Test
    public void shouldNotStoreEmptyChatMessagesWithOnlyChatStatesAndThread() {
        Message message = new Message();
        message.setType(Message.Type.chat);
        message.setThread("1234");
        PacketExtension chatState = new PacketExtension("composing", "http://jabber.org/protocol/chatstates");
        message.addExtension(chatState);
        assertFalse(OfflineMessageStore.shouldStoreMessage(message));
    }

    @Test
    public void shouldStoreEmptyChatMessagesWithOtherExtensions() {
        Message message = new Message();
        message.setType(Message.Type.chat);
        PacketExtension chatState = new PacketExtension("composing", "http://jabber.org/protocol/chatstates");
        message.addExtension(chatState);
        PacketExtension packetExtension2 = new PacketExtension("received", "urn:xmpp:receipts");
        message.addExtension(packetExtension2);
        assertTrue(OfflineMessageStore.shouldStoreMessage(message));
    }
}
