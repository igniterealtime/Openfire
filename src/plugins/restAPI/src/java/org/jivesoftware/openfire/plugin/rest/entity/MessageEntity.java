package org.jivesoftware.openfire.plugin.rest.entity;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The Class MessageEntity.
 */
@XmlRootElement(name = "message")
public class MessageEntity {

    /** The body. */
    private String body;

    /**
     * Instantiates a new message entity.
     */
    public MessageEntity() {
    }

    /**
     * Gets the body.
     *
     * @return the body
     */
    @XmlElement
    public String getBody() {
        return body;
    }

    /**
     * Sets the body.
     *
     * @param body
     *            the new body
     */
    public void setBody(String body) {
        this.body = body;
    }
}
