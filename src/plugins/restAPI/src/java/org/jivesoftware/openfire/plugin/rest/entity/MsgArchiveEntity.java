package org.jivesoftware.openfire.plugin.rest.entity;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The Class MsgArchiveEntity.
 */
@XmlRootElement(name = "archive")
public class MsgArchiveEntity {

    @XmlElement
    String jid;

    /**
     * unread messages count
     */
    @XmlElement
    int count;

    public MsgArchiveEntity() {
    }

    public MsgArchiveEntity(String jid, int count) {
        this.jid = jid;
        this.count = count;
    }

}
