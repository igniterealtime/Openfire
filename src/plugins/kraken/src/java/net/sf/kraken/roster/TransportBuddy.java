/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.roster;

import net.sf.kraken.avatars.Avatar;
import net.sf.kraken.type.NameSpace;
import net.sf.kraken.type.PresenceType;

import org.apache.log4j.Logger;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Transport Buddy.
 *
 * This class is intended to be extended and includes all necessary pieces for
 * syncing with a roster.  It also handles keeping current statuses and such for
 * easy retrieval and only sends presence changes upon status changes.  So that
 * the base transport can manage this list too sometimes, it is very important
 * that the specific transport implementation take into account that there may
 * buddy instances that do not know anything about any 'custom' fields the
 * transport may be implementing.
 *
 * @author Daniel Henninger
 */
public abstract class TransportBuddy {

    static Logger Log = Logger.getLogger(TransportBuddy.class);

    /**
     * Default constructor, nothing set up.
     */
    public TransportBuddy() {
        // Nothing
    }

    /**
     * Creates a TransportBuddy instance.
     *
     * @param manager Transport buddy manager we are associated with.
     * @param contactname The legacy contact name.
     * @param nickname The legacy nickname (can be null).
     * @param groups The list of groups the legacy contact is in (can be null).
     */
    public TransportBuddy(TransportBuddyManager manager, String contactname, String nickname, Collection<String> groups) {
        this.managerRef = new WeakReference<TransportBuddyManager>(manager);
        this.jid = manager.getSession().getTransport().convertIDToJID(contactname);
        this.contactname = manager.getSession().getTransport().convertJIDToID(this.jid);
        if (nickname != null) {
            this.nickname = nickname;
        }
        else {
            this.nickname = this.contactname;
        }
        if (groups != null && !groups.isEmpty()) {
            this.groups = groups;
        }
        if (JiveGlobals.getBooleanProperty("plugin.gateway."+getManager().getSession().getTransport().getType()+".avatars", true)) {
            try {
                this.avatar = new Avatar(this.jid);
                this.avatarSet = true;
            }
            catch (NotFoundException e) {
                // Ok then, no avatar, no worries.
            }
        }
        lastActivityTimestamp = new Date().getTime();
    }

    /**
     * The transport buddy manager we are attached to.
     */
    private WeakReference<TransportBuddyManager> managerRef = null;

    public TransportBuddyManager getManager() {
        return managerRef.get();
    }

    /**
     * ID, Screenname, name, whatever the contact name is on the legacy system
     */
    public String contactname = null;

    /**
     * Converted JID for the contact, for caching purposes
     */
    public JID jid = null;

    /**
     * A nickname associated with this contact, if it exists.
     */
    public String nickname = null;

    /**
     * A group associated with this contact, if it exists.
     */
    public Collection<String> groups = new ArrayList<String>();

    /**
     * Specific requested subscription status, if desired.
     */
    public RosterItem.SubType subtype = RosterItem.SUB_TO;

    /**
     * Specific requested ask status, if desired.
     */
    public RosterItem.AskType asktype = null;

    /**
     * Current presence status.
     */
    public PresenceType presence = PresenceType.unavailable;

    /**
     * Current verbose status.
     */
    public String verboseStatus = "";

    /**
     * Avatar instance associated with this contact.
     */
    public Avatar avatar = null;

    /**
     * Has the avatar been set?
     */
    public Boolean avatarSet = false;
    
    /**
     * Timestamp of last activity
     */
    public Long lastActivityTimestamp = null;
    
    /**
     * Retrieves timestamp of last activity.
     * 
     * @return Timestamp in milliseconds since the epoch.
     */
    public Long getLastActivityTimestamp() {
		return lastActivityTimestamp;
	}

    /**
     * Retrieves text event of last activity or null if no event text.
     * 
     * @return Text of last event.
     */
	public String getLastActivityEvent() {
		return lastActivityEvent;
	}

	/**
     * Text of last activity
     */
    public String lastActivityEvent = null;

    /**
     * Retrieves the name of the contact.
     *
     * @return Name of contact.
     */
    public String getName() {
       return contactname;
    }

    /**
     * Retrieves the JID of the contact.
     *
     *
     * @return JID of contact.
     */
    public JID getJID() {
        return jid;
    }

    /**
     * Sets the name of the contact.
     *
     * @param contactname Username of the contact.
     */
    public void setName(String contactname) {
        this.jid = getManager().getSession().getTransport().convertIDToJID(contactname);
        this.contactname = getManager().getSession().getTransport().convertJIDToID(this.jid);
    }

    /**
     * Retrieves the nickname of the contact.
     *
     * @return Nickname of contact.
     */
    public String getNickname() {
       return nickname;
    }

    /**
     * Sets the nickname of the contact.
     *
     * @param nickname Nickname of contact.
     */
    public void setNickname(String nickname) {
        Boolean changed = false;
        if (nickname != null) {
            if (this.nickname == null || !this.nickname.equals(nickname)) {
                changed = true;
            }
            this.nickname = nickname;
        }
        else {
            if (this.nickname == null || !this.nickname.equals(getName())) {
                changed = true;
            }
            this.nickname = getName();
        }
        if (changed && getManager().isActivated()) {
            Log.debug("TransportBuddy: Triggering contact update for "+this);
            getManager().getSession().updateContact(this);
        }
    }

    /**
     * Retrieves the groups of the contact.
     *
     * @return Groups contact is in.
     */
    public Collection<String> getGroups() {
       return groups;
    }

    /**
     * Sets the list of groups of the contact.
     *
     * @param groups List of groups the contact is in.
     */
    public void setGroups(List<String> groups) {
        Boolean changed = false;
        if (groups != null && !groups.isEmpty()) {
            if (this.groups == null || this.groups.isEmpty() || !groups.containsAll(this.groups) || !this.groups.containsAll(groups)) {
                changed = true;
            }
            this.groups = groups;
        }
        else {
            if (this.groups != null && !this.groups.isEmpty()) {
                changed = true;
            }
            this.groups = null;
        }
        if (changed && getManager().isActivated()) {
            Log.debug("TransportBuddy: Triggering contact update for "+this);
            getManager().getSession().updateContact(this);
        }
    }

    /**
     * Sets the nickname and list of groups of the contact.
     *
     * @param nickname Nickname of contact.
     * @param groups List of groups the contact is in.
     */
    public void setNicknameAndGroups(String nickname, List<String> groups) {
        Boolean changed = false;
        if (nickname != null) {
            if (this.nickname == null || !this.nickname.equals(nickname)) {
                changed = true;
            }
            this.nickname = nickname;
        }
        else {
            if (this.nickname == null || !this.nickname.equals(getName())) {
                changed = true;
            }
            this.nickname = getName();
        }
        if (groups != null && !groups.isEmpty()) {
            if (this.groups == null || this.groups.isEmpty() || !groups.containsAll(this.groups) || !this.groups.containsAll(groups)) {
                changed = true;
            }
            this.groups = groups;
        }
        else {
            if (this.groups != null && !this.groups.isEmpty()) {
                changed = true;
            }
            this.groups = null;
        }
        if (changed && getManager().isActivated()) {
            Log.debug("TransportBuddy: Triggering contact update for "+this);
            getManager().getSession().updateContact(this);
        }
    }

    /**
     * Retrieves the subscription status for the contact.
     *
     * @return SubType if set.
     */
    public RosterItem.SubType getSubType() {
        return subtype;
    }

    /**
     * Sets the subscription status for the contact.
     *
     * @param substatus Subscription status to be set.
     */
    public void setSubType(RosterItem.SubType substatus) {
        subtype = substatus;
    }

    /**
     * Retrieves the ask status for the contact.
     *
     * @return AskType if set.
     */
    public RosterItem.AskType getAskType() {
        return asktype;
    }

    /**
     * Sets the ask status for the contact.
     *
     * @param askstatus Ask status to be set.
     */
    public void setAskType(RosterItem.AskType askstatus) {
        asktype = askstatus;
    }

    /**
     * Retrieves the current status.
     *
     * @return Current status setting.
     */
    public PresenceType getPresence() {
        return presence;
    }

    /**
     * Sets the current status.
     *
     * @param newpresence New presence to set to.
     */
    public void setPresence(PresenceType newpresence) {
        if (newpresence == null) {
            newpresence = PresenceType.unknown;
        }
        if (newpresence.equals(PresenceType.unavailable)) {
            verboseStatus = "";
        }
        if (!presence.equals(newpresence) && newpresence != PresenceType.unknown) {
            Presence p = new Presence();
            p.setTo(getManager().getSession().getJID());
            p.setFrom(jid);
            getManager().getSession().getTransport().setUpPresencePacket(p, newpresence);
            if (!verboseStatus.equals("")) {
                p.setStatus(verboseStatus);
            }
            if (avatarSet && avatar != null) {
                Element vcard = p.addChildElement("x", NameSpace.VCARD_TEMP_X_UPDATE);
                vcard.addElement("photo").addCDATA(avatar.getXmppHash());
                vcard.addElement("hash").addCDATA(avatar.getXmppHash());
            }
            getManager().sendPacket(p);
        }
        presence = newpresence;
        lastActivityTimestamp = new Date().getTime();
    }

    /**
     * Retrieves the current verbose status.
     *
     * @return Current verbose status.
     */
    public String getVerboseStatus() {
        return verboseStatus;
    }

    /**
     * Sets the current verbose status.
     *
     * @param newstatus New verbose status.
     */
    public void setVerboseStatus(String newstatus) {
        if (newstatus == null) {
            newstatus = "";
        }
        if (!verboseStatus.equals(newstatus)) {
            Presence p = new Presence();
            p.setTo(getManager().getSession().getJID());
            p.setFrom(jid);
            getManager().getSession().getTransport().setUpPresencePacket(p, presence);
            if (!newstatus.equals("")) {
                p.setStatus(newstatus);
            }
            if (avatarSet && avatar != null) {
                Element vcard = p.addChildElement("x", NameSpace.VCARD_TEMP_X_UPDATE);
                vcard.addElement("photo").addCDATA(avatar.getXmppHash());
                vcard.addElement("hash").addCDATA(avatar.getXmppHash());
            }
            getManager().sendPacket(p);
        }
        verboseStatus = newstatus;
        lastActivityTimestamp = new Date().getTime();
        lastActivityEvent = verboseStatus;
    }

    /**
     * Convenience routine to set both presence and verbose status at the same time.
     *
     * @param newpresence New presence to set to.
     * @param newstatus New verbose status.
     */
    public void setPresenceAndStatus(PresenceType newpresence, String newstatus) {
        Log.debug("Updating status ["+newpresence+","+newstatus+"] for "+this);
        if (newpresence == null) {
            newpresence = PresenceType.unknown;
        }
        if (newstatus == null) {
            newstatus = "";
        }
        if (newpresence.equals(PresenceType.unavailable)) {
            newstatus = "";
        }
        if ((!presence.equals(newpresence) && newpresence != PresenceType.unknown) || !verboseStatus.equals(newstatus)) {
            Presence p = new Presence();
            p.setTo(getManager().getSession().getJID());
            p.setFrom(jid);
            getManager().getSession().getTransport().setUpPresencePacket(p, newpresence);
            if (!newstatus.equals("")) {
                p.setStatus(newstatus);
            }
            if (avatarSet && avatar != null) {
                Element vcard = p.addChildElement("x", NameSpace.VCARD_TEMP_X_UPDATE);
                vcard.addElement("photo").addCDATA(avatar.getXmppHash());
                vcard.addElement("hash").addCDATA(avatar.getXmppHash());
            }
            getManager().sendPacket(p);
        }
        presence = newpresence;
        verboseStatus = newstatus;
        lastActivityTimestamp = new Date().getTime();
        lastActivityEvent = verboseStatus;
    }

    /**
     * Sends the current presence to the session user.
     *
     * @param to JID to send presence updates to.
     */
    public void sendPresence(JID to) {
        // TODO: Should figure out best way to handle unknown here.
        Presence p = new Presence();
        p.setTo(to);
        p.setFrom(jid);
        getManager().getSession().getTransport().setUpPresencePacket(p, presence);
        if (verboseStatus != null && verboseStatus.length() > 0) {
            p.setStatus(verboseStatus);
        }
        if (avatarSet && avatar != null) {
            Element vcard = p.addChildElement("x", NameSpace.VCARD_TEMP_X_UPDATE);
            vcard.addElement("photo").addCDATA(avatar.getXmppHash());
            vcard.addElement("hash").addCDATA(avatar.getXmppHash());
        }
        getManager().sendPacket(p);
    }

    /**
     * Sends the current presence only if it's not unavailable.
     *
     * @param to JID to send presence updates to.
     */
    public void sendPresenceIfAvailable(JID to) {
       if (!presence.equals(PresenceType.unavailable)) {
           sendPresence(to);
       }
    }

    /**
     * Sends an offline presence for a contact only if it is currently online.
     *
     * In case you are wondering why, this is useful when a resource goes offline and we want to indicate all contacts are offline.
     *
     * @param to JID to send presence updates to.
     */
    public void sendOfflinePresenceIfAvailable(JID to) {
       if (!presence.equals(PresenceType.unavailable)) {
           Presence p = new Presence();
           p.setType(Presence.Type.unavailable);
           p.setTo(to);
           p.setFrom(jid);
           getManager().sendPacket(p);
       }
    }

    /**
     * Retrieves the cached avatar associated with this contact.
     *
     * @return The avatar associated with this contact, or null if no avatar present.
     */
    public Avatar getAvatar() {
        return avatar;
    }

    /**
     * Sets the current avatar for this contact.
     *
     * @param avatar Avatar instance to associate with this contact.
     */
    public void setAvatar(Avatar avatar) {
        boolean triggerUpdate = false;
        if (    (avatar != null && this.avatar == null) ||
                (avatar == null && this.avatar != null) ||
                (avatar != null && !this.avatar.getXmppHash().equals(avatar.getXmppHash()))) {
            triggerUpdate = true;
        }
        this.avatar = avatar;
        this.avatarSet = true;
        if (triggerUpdate) {
            Presence p = new Presence();
            p.setTo(getManager().getSession().getJID());
            p.setFrom(jid);
            getManager().getSession().getTransport().setUpPresencePacket(p, presence);
            if (!verboseStatus.equals("")) {
                p.setStatus(verboseStatus);
            }
            Element vcard = p.addChildElement("x", NameSpace.VCARD_TEMP_X_UPDATE);
            if (avatar != null) {
                vcard.addElement("photo").addCDATA(avatar.getXmppHash());
                vcard.addElement("hash").addCDATA(avatar.getXmppHash());
            }
            getManager().sendPacket(p);
        }
    }

    /**
     * Adds the PHOTO vcard element (representing an avatar) to an existing vcard.
     *
     * This will add the avatar to a vcard if there's one to add.  Otherwise will not add anything.
     * If added, a properly formatted PHOTO element with base64 encoded data in it will be added.
     * 
     * param vcard vcard to add PHOTO element to
     */
    public void addVCardPhoto(Element vcard) {
        if (!avatarSet) {
            Log.debug("TransportBuddy: I've got nothing! (no avatar set)");
            return;
        }
        Element photo = vcard.addElement("PHOTO");
        if (avatar != null) {
            try {
                photo.addElement("TYPE").addCDATA(avatar.getMimeType());
                photo.addElement("BINVAL").addCDATA(avatar.getImageData());
            }
            catch (NotFoundException e) {
                // No problem, leave it empty then.
            }
        }
    }

    /**
     * Returns the entire vcard element for an avatar.
     *
     * This will return a vCard element, filled in as much as possible, regardless of whether we have
     * real data for the user.  It'll return minimal regardless.
     *
     * @return vCard element
     */
    public Element getVCard() {
        Element vcard = DocumentHelper.createElement(QName.get("vCard", NameSpace.VCARD_TEMP));

        vcard.addElement("VERSION").addCDATA("2.0");
        vcard.addElement("JABBERID").addCDATA(getJID().toString());
        vcard.addElement("NICKNAME").addCDATA(getNickname() == null ? getName() : getNickname());

        if (JiveGlobals.getBooleanProperty("plugin.gateway."+getManager().getSession().getTransport().getType()+".avatars", true)) {
            addVCardPhoto(vcard);
        }

        return vcard;
    }

    /**
     * Outputs information about the transport buddy in a pretty format.
     */
    @Override
    public String toString() {
        return "{Buddy: "+this.jid+" (Nickname: "+this.nickname+") (Groups: "+this.groups+")}";
    }

}
