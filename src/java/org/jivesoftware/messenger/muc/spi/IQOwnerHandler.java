/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.muc.spi;

import org.jivesoftware.messenger.forms.DataForm;
import org.jivesoftware.messenger.forms.FormField;
import org.jivesoftware.messenger.forms.spi.XDataFormImpl;
import org.jivesoftware.messenger.forms.spi.XFormFieldImpl;
import org.jivesoftware.messenger.muc.ConflictException;
import org.jivesoftware.messenger.muc.ForbiddenException;
import org.jivesoftware.messenger.muc.MUCRole;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.*;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;

/**
 * A handler for the IQ packet with namespace http://jabber.org/protocol/muc#owner. This kind of 
 * packets are usually sent by room owners. So this handler provides the necessary functionality
 * to support owner requirements such as: room configuration and room destruction.
 *
 * @author Gaston Dombiak
 */
public class IQOwnerHandler {
    private MUCRoomImpl room;

    private PacketRouter router;

    private XDataFormImpl configurationForm;

    private MetaDataFragment probeResult;

    public IQOwnerHandler(MUCRoomImpl chatroom, PacketRouter packetRouter) {
        this.room = chatroom;
        this.router = packetRouter;
        init();
    }

    public void handleIQ(IQ packet, MUCRole role) throws ForbiddenException, UnauthorizedException,
            ConflictException {
        // Only owners can send packets with the namespace "http://jabber.org/protocol/muc#owner"
        if (MUCRole.OWNER != role.getAffiliation()) {
            throw new ForbiddenException();
        }

        IQ reply = packet.createResult();
        Element element = ((XMPPDOMFragment)packet.getChildFragment()).getRootElement();

        // Analyze the action to perform based on the included element
        Element formElement = element.element(QName.get("x", "jabber:x:data"));
        if (formElement != null) {
            handleDataFormElement(role, formElement, reply);
        }
        else {
            Element destroyElement = element.element("destroy");
            if (destroyElement != null) {
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
                        reply.setChildFragment(probeResult);
                    }
                    // An unknown and possibly incorrect element was included in the query
                    // element so answer a BAD_REQUEST error
                    else {
                        reply.setError(XMPPError.Code.BAD_REQUEST);
                    }
                }
            }
        }
        router.route(reply);
    }

    /**
     * Handles packets that includes item elements. Depending on the item's attributes the
     * interpretation of the request may differ. For example, an item that only contains the
     * "affiliation" attribute is requesting the list of owners or admins. Whilst if the item
     * contains the affiliation together with a jid means that the client is changing the
     * affiliation of the requested jid.
     *
     * @param itemsList  the list of items sent by the client.
     * @param senderRole the role of the user that is sent the items.
     * @param reply      the iq packet that will be sent back as a reply to the client's request.
     * @throws ForbiddenException if the user does not have enough permissions.
     */
    private void handleItemsElement(List itemsList, MUCRole senderRole, IQ reply)
            throws ForbiddenException, ConflictException {
        Element item;
        String affiliation = null;
        boolean hasJID = ((Element)itemsList.get(0)).attributeValue("jid") != null;
        boolean hasNick = ((Element)itemsList.get(0)).attributeValue("nick") != null;
        // Check if the client is requesting or changing the list of owners/admin
        if (!hasJID && !hasNick) {
            // The client is requesting the list of owners or admins
            for (Iterator items = itemsList.iterator(); items.hasNext();) {
                item = (Element)items.next();
                affiliation = item.attributeValue("affiliation");
                // Create the result that will hold an item for each owner or admin
                MetaDataFragment result = new MetaDataFragment(DocumentHelper.createElement(QName
                        .get("query", "http://jabber.org/protocol/muc#owner")));

                if ("owner".equals(affiliation)) {
                    // The client is requesting the list of owners
                    MetaDataFragment ownerMetaData;
                    String jid;
                    MUCRole role;
                    for (Iterator owners = room.getOwners(); owners.hasNext();) {
                        jid = (String)owners.next();
                        ownerMetaData =
                                new MetaDataFragment("http://jabber.org/protocol/muc#owner",
                                        "item");
                        ownerMetaData.setProperty("item:affiliation", "owner");
                        ownerMetaData.setProperty("item:jid", jid);
                        // Add role and nick to the metadata if the user is in the room
                        try {
                            List roles = room.getOccupantsByBareJID(jid);
                            role = (MUCRole)roles.get(0);
                            ownerMetaData.setProperty("item:role", role.getRoleAsString());
                            ownerMetaData.setProperty("item:nick", role.getNickname());
                        }
                        catch (UserNotFoundException e) {
                            // Do nothing
                        }
                        // Add the item with the owner's information to the result
                        result.addFragment(ownerMetaData);
                    }
                    // Add the result items to the reply
                    reply.addFragment(result);
                }
                else if ("admin".equals(affiliation)) {
                    // The client is requesting the list of admins
                    MetaDataFragment adminMetaData;
                    String jid;
                    MUCRole role;
                    for (Iterator admins = room.getAdmins(); admins.hasNext();) {
                        jid = (String)admins.next();
                        adminMetaData =
                                new MetaDataFragment("http://jabber.org/protocol/muc#owner",
                                        "item");
                        adminMetaData.setProperty("item:affiliation", "admin");
                        adminMetaData.setProperty("item:jid", jid);
                        // Add role and nick to the metadata if the user is in the room
                        try {
                            List roles = room.getOccupantsByBareJID(jid);
                            role = (MUCRole)roles.get(0);
                            adminMetaData.setProperty("item:role", role.getRoleAsString());
                            adminMetaData.setProperty("item:nick", role.getNickname());
                        }
                        catch (UserNotFoundException e) {
                            // Do nothing
                        }
                        // Add the item with the admin's information to the result
                        result.addFragment(adminMetaData);
                    }
                    // Add the result items to the reply
                    reply.addFragment(result);
                }
                else {
                    reply.setError(XMPPError.Code.BAD_REQUEST);
                }
            }
        }
        else {
            // The client is modifying the list of owners or admins
            Map jids = new HashMap();
            String bareJID = null;
            String nick;
            // Collect the new affiliations for the specified jids
            for (Iterator items = itemsList.iterator(); items.hasNext();) {
                try {
                    item = (Element)items.next();
                    affiliation = item.attributeValue("affiliation");
                    if (hasJID) {
                        bareJID = XMPPAddress.parseBareAddress(item.attributeValue("jid"));
                    }
                    else {
                        // Get the bare JID based on the requested nick
                        nick = item.attributeValue("nick");
                        bareJID = room.getOccupant(nick).getChatUser().getAddress()
                                .toBareStringPrep();
                    }
                    jids.put(bareJID, affiliation);
                }
                catch (UserNotFoundException e) {
                    // Do nothing
                }
            }

            // Keep a registry of the updated presences
            List presences = new ArrayList(jids.size());

            room.lock.readLock().lock();
            try {
                // Check if all the existing owners are being removed
                if (jids.keySet().containsAll(room.owners)) {
                    // Answer a conflict error if we are only removing ALL the owners
                    if (!jids.containsValue("owner")) {
                        reply.setError(XMPPError.Code.CONFLICT);
                        return;
                    }
                }

                room.lock.readLock().unlock();
                room.lock.writeLock().lock();
                try {
                    String targetAffiliation = null;
                    for (Iterator it = jids.keySet().iterator(); it.hasNext();) {
                        bareJID = (String)it.next();
                        targetAffiliation = (String)jids.get(bareJID);
                        if ("owner".equals(targetAffiliation)) {
                            // Add the new user as an owner of the room
                            presences.addAll(room.addOwner(bareJID, senderRole));
                        }
                        else if ("admin".equals(targetAffiliation)) {
                            // Add the new user as an admin of the room
                            presences.addAll(room.addAdmin(bareJID, senderRole));
                        }
                        else if ("member".equals(targetAffiliation)) {
                            // Add the new user as a member of the room
                            presences.addAll(room.addMember(bareJID, null, senderRole));
                        }
                        else if ("none".equals(targetAffiliation)) {
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
            try {
                for (Iterator it = presences.iterator(); it.hasNext();) {
                    room.send((Presence)it.next());
                }
            }
            catch (UnauthorizedException e) {
                // Do nothing
            }
        }
    }

    /**
     * Handles packets that includes a data form. The data form was sent using an element with name
     * "x" and namespace "jabber:x:data".
     *
     * @param senderRole  the role of the user that sent the data form.
     * @param formElement the element that contains the data form specification.
     * @param reply       the iq packet that will be sent back as a reply to the client's request.
     * @throws UnauthorizedException If the user doesn't have permission to destroy/configure the
     *                               room.
     * @throws ForbiddenException    if the user does not have enough privileges.
     */
    private void handleDataFormElement(MUCRole senderRole, Element formElement, IQ reply)
            throws UnauthorizedException, ForbiddenException, ConflictException {
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
                processConfigurationForm(completedForm, senderRole, reply);
            }
            // If the room was locked, unlock it and send to the owner the "room is now unlocked"
            // message
            if (room.isLocked()) {
                room.unlockRoom(senderRole);
            }
        }
    }

    private void processConfigurationForm(XDataFormImpl completedForm, MUCRole senderRole, IQ reply)
            throws ForbiddenException, ConflictException {
        Iterator values;
        String booleanValue;
        List list;
        FormField field;

        // Get the new list of admins
        field = completedForm.getField("muc#roomconfig_roomadmins");
        List admins = new ArrayList();
        if (field != null) {
            values = field.getValues();
            while (values.hasNext()) {
                admins.add(values.next());
            }
        }

        // Get the new list of owners
        field = completedForm.getField("muc#roomconfig_roomowners");
        List owners = new ArrayList();
        if (field != null) {
            values = field.getValues();
            while (values.hasNext()) {
                owners.add(values.next());
            }
        }

        // Answer a conflic error if all the current owners will be removed
        if (owners.isEmpty()) {
            reply.setError(XMPPError.Code.CONFLICT);
            return;
        }

        // Keep a registry of the updated presences
        List presences = new ArrayList(admins.size() + owners.size());

        room.lock.writeLock().lock();
        try {
            field = completedForm.getField("muc#roomconfig_roomname");
            if (field != null) {
                values = field.getValues();
                room.setName((values.hasNext() ? (String)values.next() : " "));
            }

            field = completedForm.getField("muc#roomconfig_roomdesc");
            if (field != null) {
                values = field.getValues();
                room.setDescription((values.hasNext() ? (String)values.next() : " "));
            }

            field = completedForm.getField("muc#roomconfig_changesubject");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? (String)values.next() : "1");
                room.setCanOccupantsChangeSubject(("1".equals(booleanValue) ? true : false));
            }

            field = completedForm.getField("muc#roomconfig_maxusers");
            if (field != null) {
                values = field.getValues();
                room
                        .setMaxUsers((values.hasNext() ? Integer.parseInt((String) values.next())
                                : 30));
            }

            field = completedForm.getField("muc#roomconfig_presencebroadcast");
            if (field != null) {
                values = field.getValues();
                list = new ArrayList();
                while (values.hasNext()) {
                    list.add(values.next());
                }
                room.setRolesToBroadcastPresence(list);
            }

            field = completedForm.getField("muc#roomconfig_publicroom");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? (String)values.next() : "1");
                room.setPublicRoom(("1".equals(booleanValue) ? true : false));
            }

            field = completedForm.getField("muc#roomconfig_persistentroom");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? (String)values.next() : "1");
                boolean isPersistent = ("1".equals(booleanValue) ? true : false);
                // Delete the room from the DB if it's no longer persistent
                if (room.isPersistent() && !isPersistent) {
                    MUCPersistenceManager.deleteFromDB(room);
                }
                room.setPersistent(isPersistent);
            }

            field = completedForm.getField("muc#roomconfig_moderatedroom");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? (String)values.next() : "1");
                room.setModerated(("1".equals(booleanValue) ? true : false));
            }

            field = completedForm.getField("muc#roomconfig_inviteonly");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? (String)values.next() : "1");
                room.setInvitationRequiredToEnter(("1".equals(booleanValue) ? true : false));
            }

            field = completedForm.getField("muc#roomconfig_allowinvites");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? (String)values.next() : "1");
                room.setCanOccupantsInvite(("1".equals(booleanValue) ? true : false));
            }

            field = completedForm.getField("muc#roomconfig_passwordprotectedroom");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? (String)values.next() : "1");
                boolean isPasswordProtected = "1".equals(booleanValue);
                if (isPasswordProtected) {
                    // The room is password protected so set the new password
                    field = completedForm.getField("muc#roomconfig_roomsecret");
                    if (field != null) {
                        values = completedForm.getField("muc#roomconfig_roomsecret").getValues();
                        room.setPassword((values.hasNext() ? (String)values.next() : null));
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
                booleanValue = (values.hasNext() ? (String)values.next() : "1");
                room.setCanAnyoneDiscoverJID(("anyone".equals(booleanValue) ? true : false));
            }

            field = completedForm.getField("muc#roomconfig_enablelogging");
            if (field != null) {
                values = field.getValues();
                booleanValue = (values.hasNext() ? (String)values.next() : "1");
                room.setLogEnabled(("1".equals(booleanValue) ? true : false));
            }
            
            if (room.isPersistent()) {
                room.saveToDB();
            }

            // Set the new owners and admins of the room
            presences.addAll(room.addOwners(owners, senderRole));
            presences.addAll(room.addAdmins(admins, senderRole));

            // Change the affiliation to "member" for the current owners that won't be neither
            // owner nor admin
            List ownersToRemove = new ArrayList(room.owners);
            ownersToRemove.removeAll(admins);
            ownersToRemove.removeAll(owners);
            String jid;
            for (Iterator it = ownersToRemove.iterator(); it.hasNext();) {
                jid = (String)it.next();
                presences.addAll(room.addMember(jid, null, senderRole));
            }

            // Change the affiliation to "member" for the current admins that won't be neither
            // owner nor admin
            List adminsToRemove = new ArrayList(room.admins);
            adminsToRemove.removeAll(admins);
            adminsToRemove.removeAll(owners);
            for (Iterator it = adminsToRemove.iterator(); it.hasNext();) {
                jid = (String)it.next();
                presences.addAll(room.addMember(jid, null, senderRole));
            }

        }
        finally {
            room.lock.writeLock().unlock();
        }

        // Send the updated presences to the room occupants
        try {
            for (Iterator it = presences.iterator(); it.hasNext();) {
                room.send((Presence)it.next());
            }
        }
        catch (UnauthorizedException e) {
            // Do nothing
        }
    }

    private void refreshConfigurationFormValues() {
        room.lock.readLock().lock();
        try {
            FormField field = configurationForm.getField("muc#roomconfig_roomname");
            field.clearValues();
            field.addValue(room.getName());

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
            for (Iterator it = room.getRolesToBroadcastPresence(); it.hasNext();) {
                field.addValue((String)it.next());
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

            field = configurationForm.getField("muc#roomconfig_inviteonly");
            field.clearValues();
            field.addValue((room.isInvitationRequiredToEnter() ? "1" : "0"));

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

            field = configurationForm.getField("muc#roomconfig_roomadmins");
            field.clearValues();
            for (Iterator it = room.getAdmins(); it.hasNext();) {
                field.addValue((String)it.next());
            }

            field = configurationForm.getField("muc#roomconfig_roomowners");
            field.clearValues();
            for (Iterator it = room.getOwners(); it.hasNext();) {
                field.addValue((String)it.next());
            }
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
        List params = new ArrayList();
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

        field = new XFormFieldImpl("muc#roomconfig_inviteonly");
        field.setType(FormField.TYPE_BOOLEAN);
        field.setLabel(LocaleUtils.getLocalizedString("muc.form.conf.owner_inviteonly"));
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
        probeResult = new MetaDataFragment(element);
        probeResult.addFragment(configurationForm);
    }
}