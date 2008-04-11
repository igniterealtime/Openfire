/**
 * $RCSfile$
 * $Revision: 1623 $
 * $Date: 2005-07-12 18:40:57 -0300 (Tue, 12 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc.spi;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.forms.DataForm;
import org.jivesoftware.openfire.forms.FormField;
import org.jivesoftware.openfire.forms.spi.XDataFormImpl;
import org.jivesoftware.openfire.forms.spi.XFormFieldImpl;
import org.jivesoftware.openfire.muc.ConflictException;
import org.jivesoftware.openfire.muc.ForbiddenException;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.CannotBeInvitedException;
import org.jivesoftware.openfire.muc.cluster.RoomUpdatedEvent;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import java.util.*;

/**
 * A handler for the IQ packet with namespace http://jabber.org/protocol/muc#owner. This kind of 
 * packets are usually sent by room owners. So this handler provides the necessary functionality
 * to support owner requirements such as: room configuration and room destruction.
 *
 * @author Gaston Dombiak
 */
public class IQOwnerHandler {
    private LocalMUCRoom room;

    private PacketRouter router;

    private XDataFormImpl configurationForm;

    private Element probeResult;

    public IQOwnerHandler(LocalMUCRoom chatroom, PacketRouter packetRouter) {
        this.room = chatroom;
        this.router = packetRouter;
        init();
    }

    /**
     * Handles the IQ packet sent by an owner of the room. Possible actions are:
     * <ul>
     * <li>Return the list of owners</li>
     * <li>Return the list of admins</li>
     * <li>Change user's affiliation to owner</li>
     * <li>Change user's affiliation to admin</li>
     * <li>Change user's affiliation to member</li>
     * <li>Change user's affiliation to none</li>
     * <li>Destroy the room</li>
     * <li>Return the room configuration within a dataform</li>
     * <li>Update the room configuration based on the sent dataform</li>
     * </ul>
     *
     * @param packet the IQ packet sent by an owner of the room.
     * @param role the role of the user that sent the packet.
     * @throws ForbiddenException if the user does not have enough permissions (ie. is not an owner).
     * @throws ConflictException If the room was going to lose all of its owners.
     */
    public void handleIQ(IQ packet, MUCRole role) throws ForbiddenException, ConflictException, CannotBeInvitedException {
        // Only owners can send packets with the namespace "http://jabber.org/protocol/muc#owner"
        if (MUCRole.Affiliation.owner != role.getAffiliation()) {
            throw new ForbiddenException();
        }

        IQ reply = IQ.createResultIQ(packet);
        Element element = packet.getChildElement();

        // Analyze the action to perform based on the included element
        Element formElement = element.element(QName.get("x", "jabber:x:data"));
        if (formElement != null) {
            handleDataFormElement(role, formElement);
        }
        else {
            Element destroyElement = element.element("destroy");
            if (destroyElement != null) {
                if (((MultiUserChatServiceImpl)room.getMUCService()).getMUCDelegate() != null) {
                    if (!((MultiUserChatServiceImpl)room.getMUCService()).getMUCDelegate().destroyingRoom(room.getName(), role.getUserAddress())) {
                        // Delegate said no, reject destroy request.
                        throw new ForbiddenException();
                    }
                }

                room.destroyRoom(destroyElement.attributeValue("jid"), destroyElement
                        .elementTextTrim("reason"));
            }
            else {
                List itemsList = element.elements("item");
                if (!itemsList.isEmpty()) {
                    handleItemsElement(itemsList, role, reply);
                }
                else {
                    // If no element was included in the query element then answer the
                    // configuration form
                    if (!element.elementIterator().hasNext()) {
                        refreshConfigurationFormValues();
                        reply.setChildElement(probeResult.createCopy());
                    }
                    // An unknown and possibly incorrect element was included in the query
                    // element so answer a BAD_REQUEST error
                    else {
                        reply.setChildElement(packet.getChildElement().createCopy());
                        reply.setError(PacketError.Condition.bad_request);
                    }
                }
            }
        }
        if (reply.getTo() != null) {
            // Send a reply only if the sender of the original packet was from a real JID. (i.e. not
            // a packet generated locally)
            router.route(reply);
        }
    }

    /**
     * Handles packets that includes item elements. Depending on the item's attributes the
     * interpretation of the request may differ. For example, an item that only contains the
     * "affiliation" attribute is requesting the list of owners or admins. Whilst if the item
     * contains the affiliation together with a jid means that the client is changing the
     * affiliation of the requested jid.
     *
     * @param itemsList  the list of items sent by the client.
     * @param senderRole the role of the user that sent the items.
     * @param reply      the iq packet that will be sent back as a reply to the client's request.
     * @throws ForbiddenException if the user does not have enough permissions.
     * @throws ConflictException If the room was going to lose all of its owners.
     * @throws CannotBeInvitedException If the user being invited as a result of being added to a members-only room still does not have permission
     */
    private void handleItemsElement(List itemsList, MUCRole senderRole, IQ reply)
            throws ForbiddenException, ConflictException, CannotBeInvitedException {
        Element item;
        boolean hasJID = ((Element)itemsList.get(0)).attributeValue("jid") != null;
        boolean hasNick = ((Element)itemsList.get(0)).attributeValue("nick") != null;
        // Check if the client is requesting or changing the list of owners/admin
        if (!hasJID && !hasNick) {
            // The client is requesting the list of owners or admins
            for (Object anItem : itemsList) {
                item = (Element) anItem;
                String affiliation = item.attributeValue("affiliation");
                // Create the result that will hold an item for each owner or admin
                Element result = reply.setChildElement("query", "http://jabber.org/protocol/muc#owner");

                if ("owner".equals(affiliation)) {
                    // The client is requesting the list of owners
                    Element ownerMetaData;
                    MUCRole role;
                    for (String jid : room.getOwners()) {
                        ownerMetaData = result.addElement("item", "http://jabber.org/protocol/muc#owner");
                        ownerMetaData.addAttribute("affiliation", "owner");
                        ownerMetaData.addAttribute("jid", jid);
                        // Add role and nick to the metadata if the user is in the room
                        try {
                            List<MUCRole> roles = room.getOccupantsByBareJID(jid);
                            role = roles.get(0);
                            ownerMetaData.addAttribute("role", role.getRole().toString());
                            ownerMetaData.addAttribute("nick", role.getNickname());
                        }
                        catch (UserNotFoundException e) {
                            // Do nothing
                        }
                    }
                } else if ("admin".equals(affiliation)) {
                    // The client is requesting the list of admins
                    Element adminMetaData;
                    MUCRole role;
                    for (String jid : room.getAdmins()) {
                        adminMetaData = result.addElement("item", "http://jabber.org/protocol/muc#owner");
                        adminMetaData.addAttribute("affiliation", "admin");
                        adminMetaData.addAttribute("jid", jid);
                        // Add role and nick to the metadata if the user is in the room
                        try {
                            List<MUCRole> roles = room.getOccupantsByBareJID(jid);
                            role = roles.get(0);
                            adminMetaData.addAttribute("role", role.getRole().toString());
                            adminMetaData.addAttribute("nick", role.getNickname());
                        }
                        catch (UserNotFoundException e) {
                            // Do nothing
                        }
                    }
                } else {
                    reply.setError(PacketError.Condition.bad_request);
                }
            }
        }
        else {
            // The client is modifying the list of owners or admins
            Map<String,String> jids = new HashMap<String,String>();
            String nick;
            // Collect the new affiliations for the specified jids
            for (Object anItem : itemsList) {
                try {
                    item = (Element) anItem;
                    String affiliation = item.attributeValue("affiliation");
                    String bareJID;
                    if (hasJID) {
                        bareJID = new JID(item.attributeValue("jid")).toBareJID();
                    } else {
                        // Get the bare JID based on the requested nick
                        nick = item.attributeValue("nick");
                        bareJID = room.getOccupant(nick).getUserAddress().toBareJID();
                    }
                    jids.put(bareJID, affiliation);
                }
                catch (UserNotFoundException e) {
                    // Do nothing
                }
            }

            // Keep a registry of the updated presences
            List<Presence> presences = new ArrayList<Presence>(jids.size());

            room.lock.readLock().lock();
            try {
                // Check if all the existing owners are being removed
                if (jids.keySet().containsAll(room.owners)) {
                    // Answer a conflict error if we are only removing ALL the owners
                    if (!jids.containsValue("owner")) {
                        throw new ConflictException();
                    }
                }

                room.lock.readLock().unlock();
                room.lock.writeLock().lock();
                try {
                    for (String bareJID : jids.keySet()) {
                        String targetAffiliation = jids.get(bareJID);
                        if ("owner".equals(targetAffiliation)) {
                            // Add the new user as an owner of the room
                            presences.addAll(room.addOwner(bareJID, senderRole));
                        } else if ("admin".equals(targetAffiliation)) {
                            // Add the new user as an admin of the room
                            presences.addAll(room.addAdmin(bareJID, senderRole));
                        } else if ("member".equals(targetAffiliation)) {
                            // Add the new user as a member of the room
                            boolean hadAffiliation = room.getAffiliation(bareJID) != MUCRole.Affiliation.none;
                            presences.addAll(room.addMember(bareJID, null, senderRole));
                            // If the user had an affiliation don't send an invitation. Otherwise
                            // send an invitation if the room is members-only
                            if (!hadAffiliation && room.isMembersOnly()) {
                                room.sendInvitation(new JID(bareJID), null, senderRole, null);
                            }
                        } else if ("none".equals(targetAffiliation)) {
                            // Set that this jid has a NONE affiliation
                            presences.addAll(room.addNone(bareJID, senderRole));
                        }
                    }
                }
                finally {
                    room.lock.writeLock().unlock();
                    room.lock.readLock().lock();
                }
            }
            finally {
                room.lock.readLock().unlock();
            }

            // Send the updated presences to the room occupants
            for (Presence presence : presences) {
                room.send(presence);
            }
        }
    }

    /**
     * Handles packets that includes a data form. The data form was sent using an element with name
     * "x" and namespace "jabber:x:data".
     *
     * @param senderRole  the role of the user that sent the data form.
     * @param formElement the element that contains the data form specification.
     * @throws ForbiddenException    if the user does not have enough privileges.
     * @throws ConflictException If the room was going to lose all of its owners.
     */
    private void handleDataFormElement(MUCRole senderRole, Element formElement)
            throws ForbiddenException, ConflictException {
        XDataFormImpl completedForm = new XDataFormImpl();
        completedForm.parse(formElement);

        if (DataForm.TYPE_CANCEL.equals(completedForm.getType())) {
            // If the room was just created (i.e. is locked) and the owner cancels the configuration
            // form then destroy the room
            if (room.isLocked()) {
                room.destroyRoom(null, null);
            }
        }
        else if (DataForm.TYPE_SUBMIT.equals(completedForm.getType())) {
            // The owner is requesting an instant room
            if (completedForm.getFieldsSize() == 0) {
                // Do nothing
            }
            // The owner is requesting a reserved room or is changing the current configuration
            else {
                processConfigurationForm(completedForm, senderRole);
            }
            // If the room was locked, unlock it and send to the owner the "room is now unlocked"
            // message
            if (room.isLocked() && !room.isManuallyLocked()) {
                room.unlock(senderRole);
            }
            if (!room.isDestroyed) {
                // Let other cluster nodes that the room has been updated
                CacheFactory.doClusterTask(new RoomUpdatedEvent(room));
            }
        }
    }

    /**
     * Processes the completed form sent by an owner of the room. This will modify the room's
     * configuration as well as the list of owners and admins.
     *
     * @param completedForm the completed form sent by an owner of the room.
     * @param senderRole the role of the user that sent the completed form.
     * @throws ForbiddenException if the user does not have enough privileges.
     * @throws ConflictException If the room was going to lose all of its owners.
     */
    private void processConfigurationForm(XDataFormImpl completedForm, MUCRole senderRole)
            throws ForbiddenException, ConflictException {
        Iterator<String> values;
        String booleanValue;
        List<String> list;
        FormField field;

        // Get the new list of admins
        field = completedForm.getField("muc#roomconfig_roomadmins");
        boolean adminsSent = field != null;
        List<String> admins = new ArrayList<String>();
        if (field != null) {
            values = field.getValues();
            while (values.hasNext()) {
                admins.add(values.next());
            }
        }

        // Get the new list of owners
        field = completedForm.getField("muc#roomconfig_roomowners");
        boolean ownersSent = field != null;
        List<String> owners = new ArrayList<String>();
        if (field != null) {
            values = field.getValues();
            while (values.hasNext()) {
                owners.add(values.next());
            }
        }

        // Answer a conflic error if all the current owners will be removed
        if (ownersSent && owners.isEmpty()) {
            throw new ConflictException();
        }

        // Keep a registry of the updated presences
        List<Presence> presences = new ArrayList<Presence>(admins.size() + owners.size());

        room.lock.writeLock().lock();
        try {
            field = completedForm.getField("muc#roomconfig_roomname");
            if (field != null) {
                values = field.getValues();
                room.setNaturalLanguageName((values.hasNext() ? values.next() : " "));
            }

            field = completedForm.getField("muc#roomconfig_roomdesc");
            if (field != null) {
                values = field.getValues();
                room.setDescription((values.hasNext() ? values.next() : " "));
            }

            field = completedForm.getField("muc#roomconfig_changesubject");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                room.setCanOccupantsChangeSubject(("1".equals(booleanValue)));
            }

            field = completedForm.getField("muc#roomconfig_maxusers");
            if (field != null) {
                values = field.getValues();
                room.setMaxUsers((values.hasNext() ? Integer.parseInt(values.next()) : 30));
            }

            field = completedForm.getField("muc#roomconfig_presencebroadcast");
            if (field != null) {
                values = field.getValues();
                list = new ArrayList<String>();
                while (values.hasNext()) {
                    list.add(values.next());
                }
                room.setRolesToBroadcastPresence(list);
            }

            field = completedForm.getField("muc#roomconfig_publicroom");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                room.setPublicRoom(("1".equals(booleanValue)));
            }

            field = completedForm.getField("muc#roomconfig_persistentroom");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                boolean isPersistent = ("1".equals(booleanValue));
                // Delete the room from the DB if it's no longer persistent
                if (room.isPersistent() && !isPersistent) {
                    MUCPersistenceManager.deleteFromDB(room);
                }
                room.setPersistent(isPersistent);
            }

            field = completedForm.getField("muc#roomconfig_moderatedroom");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                room.setModerated(("1".equals(booleanValue)));
            }

            field = completedForm.getField("muc#roomconfig_membersonly");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                presences.addAll(room.setMembersOnly(("1".equals(booleanValue))));
            }

            field = completedForm.getField("muc#roomconfig_allowinvites");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                room.setCanOccupantsInvite(("1".equals(booleanValue)));
            }

            field = completedForm.getField("muc#roomconfig_passwordprotectedroom");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                boolean isPasswordProtected = "1".equals(booleanValue);
                if (isPasswordProtected) {
                    // The room is password protected so set the new password
                    field = completedForm.getField("muc#roomconfig_roomsecret");
                    if (field != null) {
                        values = completedForm.getField("muc#roomconfig_roomsecret").getValues();
                        room.setPassword((values.hasNext() ? values.next() : null));
                    }
                }
                else {
                    // The room is not password protected so remove any previous password
                    room.setPassword(null);
                }
            }

            field = completedForm.getField("muc#roomconfig_whois");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                room.setCanAnyoneDiscoverJID(("anyone".equals(booleanValue)));
            }

            field = completedForm.getField("muc#roomconfig_enablelogging");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                room.setLogEnabled(("1".equals(booleanValue)));
            }

            field = completedForm.getField("x-muc#roomconfig_reservednick");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                room.setLoginRestrictedToNickname(("1".equals(booleanValue)));
            }

            field = completedForm.getField("x-muc#roomconfig_canchangenick");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                room.setChangeNickname(("1".equals(booleanValue)));
            }

            field = completedForm.getField("x-muc#roomconfig_registration");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? values.next() : "1");
                room.setRegistrationEnabled(("1".equals(booleanValue)));
            }

            // Update the modification date to reflect the last time when the room's configuration
            // was modified
            room.setModificationDate(new Date());

            if (room.isPersistent()) {
                room.saveToDB();
            }

            // Set the new owners and admins of the room
            presences.addAll(room.addOwners(owners, senderRole));
            presences.addAll(room.addAdmins(admins, senderRole));

            if (ownersSent) {
                // Change the affiliation to "member" for the current owners that won't be neither
                // owner nor admin (if the form included the owners field)
                List<String> ownersToRemove = new ArrayList<String>(room.owners);
                ownersToRemove.removeAll(admins);
                ownersToRemove.removeAll(owners);
                for (String jid : ownersToRemove) {
                    presences.addAll(room.addMember(jid, null, senderRole));
                }
            }

            if (adminsSent) {
                // Change the affiliation to "member" for the current admins that won't be neither
                // owner nor admin (if the form included the admins field)
                List<String> adminsToRemove = new ArrayList<String>(room.admins);
                adminsToRemove.removeAll(admins);
                adminsToRemove.removeAll(owners);
                for (String jid : adminsToRemove) {
                    presences.addAll(room.addMember(jid, null, senderRole));
                }
            }

            // Destroy the room if the room is no longer persistent and there are no occupants in
            // the room
            if (!room.isPersistent() && room.getOccupantsCount() == 0) {
                room.destroyRoom(null, null);
            }

        }
        finally {
            room.lock.writeLock().unlock();
        }

        // Send the updated presences to the room occupants
        for (Object presence : presences) {
            room.send((Presence) presence);
        }
    }

    private void refreshConfigurationFormValues() {
        room.lock.readLock().lock();
        try {
            FormField field = configurationForm.getField("muc#roomconfig_roomname");
            field.clearValues();
            field.addValue(room.getNaturalLanguageName());

            field = configurationForm.getField("muc#roomconfig_roomdesc");
            field.clearValues();
            field.addValue(room.getDescription());

            field = configurationForm.getField("muc#roomconfig_changesubject");
            field.clearValues();
            field.addValue((room.canOccupantsChangeSubject() ? "1" : "0"));

            field = configurationForm.getField("muc#roomconfig_maxusers");
            field.clearValues();
            field.addValue(Integer.toString(room.getMaxUsers()));

            field = configurationForm.getField("muc#roomconfig_presencebroadcast");
            field.clearValues();
            for (String roleToBroadcast : room.getRolesToBroadcastPresence()) {
                field.addValue(roleToBroadcast);
            }

            field = configurationForm.getField("muc#roomconfig_publicroom");
            field.clearValues();
            field.addValue((room.isPublicRoom() ? "1" : "0"));

            field = configurationForm.getField("muc#roomconfig_persistentroom");
            field.clearValues();
            field.addValue((room.isPersistent() ? "1" : "0"));

            field = configurationForm.getField("muc#roomconfig_moderatedroom");
            field.clearValues();
            field.addValue((room.isModerated() ? "1" : "0"));

            field = configurationForm.getField("muc#roomconfig_membersonly");
            field.clearValues();
            field.addValue((room.isMembersOnly() ? "1" : "0"));

            field = configurationForm.getField("muc#roomconfig_allowinvites");
            field.clearValues();
            field.addValue((room.canOccupantsInvite() ? "1" : "0"));

            field = configurationForm.getField("muc#roomconfig_passwordprotectedroom");
            field.clearValues();
            field.addValue((room.isPasswordProtected() ? "1" : "0"));

            field = configurationForm.getField("muc#roomconfig_roomsecret");
            field.clearValues();
            field.addValue(room.getPassword());

            field = configurationForm.getField("muc#roomconfig_whois");
            field.clearValues();
            field.addValue((room.canAnyoneDiscoverJID() ? "anyone" : "moderators"));

            field = configurationForm.getField("muc#roomconfig_enablelogging");
            field.clearValues();
            field.addValue((room.isLogEnabled() ? "1" : "0"));

            field = configurationForm.getField("x-muc#roomconfig_reservednick");
            field.clearValues();
            field.addValue((room.isLoginRestrictedToNickname() ? "1" : "0"));

            field = configurationForm.getField("x-muc#roomconfig_canchangenick");
            field.clearValues();
            field.addValue((room.canChangeNickname() ? "1" : "0"));

            field = configurationForm.getField("x-muc#roomconfig_registration");
            field.clearValues();
            field.addValue((room.isRegistrationEnabled() ? "1" : "0"));

            field = configurationForm.getField("muc#roomconfig_roomadmins");
            field.clearValues();
            for (String jid : room.getAdmins()) {
                field.addValue(jid);
            }

            field = configurationForm.getField("muc#roomconfig_roomowners");
            field.clearValues();
            for (String jid : room.getOwners()) {
                field.addValue(jid);
            }

            // Remove the old element
            probeResult.remove(probeResult.element(QName.get("x", "jabber:x:data")));
            // Add the new representation of configurationForm as an element 
            probeResult.add(configurationForm.asXMLElement());

        }
        finally {
            room.lock.readLock().unlock();
        }
    }

    private void init() {
        Element element = DocumentHelper.createElement(QName.get("query",
                "http://jabber.org/protocol/muc#owner"));

        configurationForm = new XDataFormImpl(DataForm.TYPE_FORM);
        configurationForm.setTitle(LocaleUtils.getLocalizedString("muc.form.conf.title"));
        List<String> params = new ArrayList<String>();
        params.add(room.getName());
        configurationForm.addInstruction(LocaleUtils.getLocalizedString("muc.form.conf.instruction", params));

        XFormFieldImpl field = new XFormFieldImpl("FORM_TYPE");
        field.setType(FormField.TYPE_HIDDEN);
        field.addValue("http://jabber.org/protocol/muc#roomconfig");
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_roomname");
        field.setType(FormField.TYPE_TEXT_SINGLE);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_roomname"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_roomdesc");
        field.setType(FormField.TYPE_TEXT_SINGLE);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_roomdesc"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_changesubject");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_changesubject"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_maxusers");
        field.setType(FormField.TYPE_LIST_SINGLE);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_maxusers"));
        field.addOption("10", "10");
        field.addOption("20", "20");
        field.addOption("30", "30");
        field.addOption("40", "40");
        field.addOption("50", "50");
        field.addOption(LocaleUtils.getLocalizedString("muc.form.conf.none"), "0");
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_presencebroadcast");
        field.setType(FormField.TYPE_LIST_MULTI);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_presencebroadcast"));
        field.addOption(LocaleUtils.getLocalizedString("muc.form.conf.moderator"), "moderator");
        field.addOption(LocaleUtils.getLocalizedString("muc.form.conf.participant"), "participant");
        field.addOption(LocaleUtils.getLocalizedString("muc.form.conf.visitor"), "visitor");
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_publicroom");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_publicroom"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_persistentroom");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_persistentroom"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_moderatedroom");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_moderatedroom"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_membersonly");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_membersonly"));
        configurationForm.addField(field);

        field = new XFormFieldImpl();
        field.setType(FormField.TYPE_FIXED);
        field.addValue(LocaleUtils.getLocalizedString("muc.form.conf.allowinvitesfixed"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_allowinvites");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_allowinvites"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_passwordprotectedroom");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_passwordprotectedroom"));
        configurationForm.addField(field);

        field = new XFormFieldImpl();
        field.setType(FormField.TYPE_FIXED);
        field.addValue(LocaleUtils.getLocalizedString("muc.form.conf.roomsecretfixed"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_roomsecret");
        field.setType(FormField.TYPE_TEXT_PRIVATE);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_roomsecret"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_whois");
        field.setType(FormField.TYPE_LIST_SINGLE);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_whois"));
        field.addOption(LocaleUtils.getLocalizedString("muc.form.conf.moderator"), "moderators");
        field.addOption(LocaleUtils.getLocalizedString("muc.form.conf.anyone"), "anyone");
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_enablelogging");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_enablelogging"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("x-muc#roomconfig_reservednick");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_reservednick"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("x-muc#roomconfig_canchangenick");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_canchangenick"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("x-muc#roomconfig_registration");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_registration"));
        configurationForm.addField(field);

        field = new XFormFieldImpl();
        field.setType(FormField.TYPE_FIXED);
        field.addValue(LocaleUtils.getLocalizedString("muc.form.conf.roomadminsfixed"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_roomadmins");
        field.setType(FormField.TYPE_JID_MULTI);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_roomadmins"));
        configurationForm.addField(field);

        field = new XFormFieldImpl();
        field.setType(FormField.TYPE_FIXED);
        field.addValue(LocaleUtils.getLocalizedString("muc.form.conf.roomownersfixed"));
        configurationForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_roomowners");
        field.setType(FormField.TYPE_JID_MULTI);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_roomowners"));
        configurationForm.addField(field);

        // Create the probeResult and add the basic info together with the configuration form
        probeResult = element;
        probeResult.add(configurationForm.asXMLElement());
    }
}