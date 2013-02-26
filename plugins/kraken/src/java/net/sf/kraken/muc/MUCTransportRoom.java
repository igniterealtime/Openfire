/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.muc;

import org.xmpp.packet.JID;

/**
 * Simple class to represent some information about a MUC room in a standard format.
 *
 * @author Daniel Henninger
 */
public class MUCTransportRoom {

    public MUCTransportRoom(JID roomjid, String name) {
        this.jid = roomjid;
        this.name = name;
    }

    /* JID of the MUC room */
    public JID jid;

    /* Descriptive name of the MUC room */
    public String name;

    /* Is the room password protected? */
    public Boolean password_protected = false;

    /* Is the room hidden? */
    public Boolean hidden = false;

    /* Is the room temporary? */
    public Boolean temporary = false;

    /* Is the room open? */
    public Boolean open = false;

    /* Is the room moderated? */
    public Boolean moderated = false;

    /* Is the room anonymous? */
    public Boolean anonymous = false;

    /* JID who is in charge of the room */
    public JID contact = null;

    /* Subject/topic of the room */
    public String topic = null;

    /* Primary language of the room */
    public String language = null;

    /* Location of logs for the room */
    public String log_location = null;

    /* Number of occupants of the room */
    public Integer occupant_count = null;

    public JID getJid() {
        return jid;
    }

    public void setJid(JID jid) {
        this.jid = jid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getPassword_protected() {
        return password_protected;
    }

    public void setPassword_protected(Boolean password_protected) {
        this.password_protected = password_protected;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public Boolean getTemporary() {
        return temporary;
    }

    public void setTemporary(Boolean temporary) {
        this.temporary = temporary;
    }

    public Boolean getOpen() {
        return open;
    }

    public void setOpen(Boolean open) {
        this.open = open;
    }

    public Boolean getModerated() {
        return moderated;
    }

    public void setModerated(Boolean moderated) {
        this.moderated = moderated;
    }

    public Boolean getAnonymous() {
        return anonymous;
    }

    public void setAnonymous(Boolean anonymous) {
        this.anonymous = anonymous;
    }

    public JID getContact() {
        return contact;
    }

    public void setContact(JID contact) {
        this.contact = contact;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLog_location() {
        return log_location;
    }

    public void setLog_location(String log_location) {
        this.log_location = log_location;
    }

    public Integer getOccupant_count() {
        return occupant_count;
    }

    public void setOccupant_count(Integer occupant_count) {
        this.occupant_count = occupant_count;
    }
    
}
