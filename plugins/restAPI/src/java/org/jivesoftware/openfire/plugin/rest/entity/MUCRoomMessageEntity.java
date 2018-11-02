package org.jivesoftware.openfire.plugin.rest.entity;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


//xmlns=&quot;jabber:x:event&quot;&gt;&lt;composing/&gt;&lt;/x&gt;&lt;/message&gt
@XmlRootElement(name = "message")
@XmlType(propOrder = { "to", "from", "type", "body", "delayStamp", "delayFrom"})
public class MUCRoomMessageEntity {
    String to;
    String from;
    String type;
    String body;
    String delayStamp;
    String delayFrom;

    @XmlElement
    public String getTo() {
        return to;
    }
    public void setTo(String to) {
        this.to = to;
    }

    @XmlElement
    public String getFrom() {
        return from;
    }
    public void setFrom(String from) {
        this.from = from;
    }

    @XmlElement
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    @XmlElement(name="delay_stamp")
    public String getDelayStamp() { return delayStamp; }
    public void setDelayStamp(String delayStamp) {
        this.delayStamp = delayStamp;
    }

    @XmlElement
    public String getBody() {
        return body;
    }
    public void setBody(String body) {
        this.body = body;
    }

    @XmlElement(name="delay_from")
    public String getDelayFrom() { return delayFrom; }
    public void setDelayFrom(String delayFrom) { this.delayFrom = delayFrom; }

}
