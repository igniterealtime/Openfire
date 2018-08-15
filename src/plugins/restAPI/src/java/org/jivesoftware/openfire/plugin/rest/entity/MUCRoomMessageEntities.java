package org.jivesoftware.openfire.plugin.rest.entity;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "messages")
public class MUCRoomMessageEntities {
    List<MUCRoomMessageEntity> messages;

    public MUCRoomMessageEntities() {
    }

    public MUCRoomMessageEntities(List<MUCRoomMessageEntity> messages) {
        this.messages = messages;
    }

    @XmlElement(name = "message")
    public List<MUCRoomMessageEntity> getMessages() {
        return messages;
    }

    public void setMessages(List<MUCRoomMessageEntity> messages) {
        this.messages = messages;
    }
}
