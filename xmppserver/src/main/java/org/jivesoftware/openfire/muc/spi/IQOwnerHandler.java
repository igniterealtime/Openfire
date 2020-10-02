/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.muc.spi;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupJID;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.muc.cluster.RoomUpdatedEvent;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.forms.FormField.Type;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * A handler for the IQ packet with namespace http://jabber.org/protocol/muc#owner. This kind of 
 * packets are usually sent by room owners. So this handler provides the necessary functionality
 * to support owner requirements such as: room configuration and room destruction.
 *
 * @author Gaston Dombiak
 */
public class IQOwnerHandler {
    
    private static final Logger Log = LoggerFactory.getLogger(IQOwnerHandler.class);

    private final LocalMUCRoom room;

    private final PacketRouter router;

    private DataForm configurationForm;

    private Element probeResult;

    private final boolean skipInvite;

    public IQOwnerHandler(LocalMUCRoom chatroom, PacketRouter packetRouter) {
        this.room = chatroom;
        this.router = packetRouter;
        this.skipInvite = JiveGlobals.getBooleanProperty(
                "xmpp.muc.skipInvite", false);
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
     * @throws CannotBeInvitedException never
     * @throws NotAcceptableException if the room requires a password that was not supplied
     */
    public void handleIQ(IQ packet, MUCRole role) throws ForbiddenException, ConflictException, CannotBeInvitedException, NotAcceptableException
    {
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

                JID alternateJID = null;
                final String jid = destroyElement.attributeValue("jid");
                if (jid != null) {
                    alternateJID = new JID(jid);
                }
                room.destroyRoom(alternateJID, destroyElement.elementTextTrim("reason"));
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
        if (reply.getTo() != null) {
            // Send a reply only if the sender of the original packet was from a real JID. (i.e. not
            // a packet generated locally)
            router.route(reply);
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
     * @throws NotAcceptableException if the room requires a password that was not supplied
     */
    private void handleDataFormElement(MUCRole senderRole, Element formElement)
            throws ForbiddenException, ConflictException, NotAcceptableException {
        DataForm completedForm = new DataForm(formElement);

        switch(completedForm.getType()) {
        case cancel:
            // If the room was just created (i.e. is locked) and the owner cancels the configuration
            // form then destroy the room
            if (room.isLocked()) {
                room.destroyRoom(null, null);
            }
            break;
            
        case submit:
            // The owner is requesting an instant room
            if (completedForm.getFields().isEmpty()) {
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
            break;
            
        default:
            Log.warn("cannot handle data form element: " + formElement.asXML());
            break;
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
     * @throws NotAcceptableException if the room requires a password that was not supplied
     */
    private void processConfigurationForm(DataForm completedForm, MUCRole senderRole)
            throws ForbiddenException, ConflictException, NotAcceptableException
    {
        List<String> values;
        FormField field;

        // Get the new list of admins
        field = completedForm.getField("muc#roomconfig_roomadmins");
        boolean adminsSent = field != null;
        List<JID> admins = new ArrayList<>();
        if (field != null) {
            for (String value : field.getValues()) {
                // XEP-0045: "Affiliations are granted, revoked, and 
                // maintained based on the user's bare JID, (...)"
                if (value != null && value.trim().length() != 0) {
                    // could be a group jid
                    admins.add(GroupJID.fromString((value.trim())).asBareJID());
                }
            }
        }

        // Get the new list of owners
        field = completedForm.getField("muc#roomconfig_roomowners");
        boolean ownersSent = field != null;
        List<JID> owners = new ArrayList<>();
        if (field != null) {
            for(String value : field.getValues()) {
                // XEP-0045: "Affiliations are granted, revoked, and 
                // maintained based on the user's bare JID, (...)"
                if (value != null && value.trim().length() != 0) {
                    // could be a group jid
                    owners.add(GroupJID.fromString((value.trim())).asBareJID());
                }
            }
        }

        // Answer a conflict error if all the current owners will be removed
        if (ownersSent && owners.isEmpty()) {
            throw new ConflictException();
        }

        // Keep a registry of the updated presences
        List<Presence> presences = new ArrayList<>(admins.size() + owners.size());

        field = completedForm.getField("muc#roomconfig_roomname");
        if (field != null) {
            final String value = field.getFirstValue();
            room.setNaturalLanguageName((value != null ? value : " "));
        }

        field = completedForm.getField("muc#roomconfig_roomdesc");
        if (field != null) {
            final String value = field.getFirstValue();
            room.setDescription((value != null ? value : " "));
        }

        field = completedForm.getField("muc#roomconfig_changesubject");
        if (field != null) {
            room.setCanOccupantsChangeSubject( parseFirstValueAsBoolean( field, true ) );
        }

        field = completedForm.getField("muc#roomconfig_maxusers");
        if (field != null) {
            final String value = field.getFirstValue();
            room.setMaxUsers((value != null ? Integer.parseInt(value) : 30));
        }

        field = completedForm.getField("muc#roomconfig_presencebroadcast");
        if (field != null) {
            values = new ArrayList<>(field.getValues());
            room.setRolesToBroadcastPresence(values);
        }

        field = completedForm.getField("muc#roomconfig_publicroom");
        if (field != null) {
            room.setPublicRoom( parseFirstValueAsBoolean( field, true ) );
        }

        field = completedForm.getField("muc#roomconfig_persistentroom");
        if (field != null) {
            boolean isPersistent = parseFirstValueAsBoolean( field, true );
            // Delete the room from the DB if it's no longer persistent
            if (room.isPersistent() && !isPersistent) {
                MUCPersistenceManager.deleteFromDB(room);
            }
            room.setPersistent(isPersistent);
        }

        field = completedForm.getField("muc#roomconfig_moderatedroom");
        if (field != null) {
            room.setModerated( parseFirstValueAsBoolean( field, true ) );
        }

        field = completedForm.getField("muc#roomconfig_membersonly");
        if (field != null) {
            presences.addAll(room.setMembersOnly( parseFirstValueAsBoolean( field, true ) ) );
        }

        field = completedForm.getField("muc#roomconfig_allowinvites");
        if (field != null) {
            room.setCanOccupantsInvite( parseFirstValueAsBoolean( field, true ) );
        }


        boolean passwordProtectionChanged = false;
        boolean passwordChanged = false;

        boolean updatedIsPasswordProtected = false;
        String updatedPassword = null;

        field = completedForm.getField("muc#roomconfig_passwordprotectedroom");
        if (field != null)
        {
            passwordProtectionChanged = true;
            updatedIsPasswordProtected = parseFirstValueAsBoolean( field, true );
        }

        field = completedForm.getField("muc#roomconfig_roomsecret");
        if (field != null) {
            passwordChanged = true;
            updatedPassword = completedForm.getField("muc#roomconfig_roomsecret").getFirstValue();
            if ( updatedPassword != null && updatedPassword.isEmpty() )
            {
                updatedPassword = null;
            }
        }

        if ( passwordProtectionChanged )
        {
            // The owner signifies that a change in password-protection status is desired.
            if ( !updatedIsPasswordProtected )
            {
                // The owner lifts password protection.
                room.setPassword( null );
            }
            else if ( updatedPassword == null && room.getPassword() == null )
            {
                // The owner sets password-protection, but does not provide a password (and the room does not already have a password).
                throw new NotAcceptableException( "Room is made password-protected, but is missing a password." );
            }
            else if ( updatedPassword != null )
            {
                // The owner sets password-protection and provided a new password.
                room.setPassword( updatedPassword );
            }
        }
        else if ( passwordChanged )
        {
            // The owner did not explicitly signal a password protection change, but did change the password value.
            // This implies a change in password protection.
            room.setPassword( updatedPassword );
        }

        field = completedForm.getField("muc#roomconfig_whois");
        if (field != null) {
            room.setCanAnyoneDiscoverJID(("anyone".equals(field.getFirstValue())));
        }

        field = completedForm.getField("muc#roomconfig_allowpm");
        if (field != null) {
            room.setCanSendPrivateMessage(field.getFirstValue());
        }

        field = completedForm.getField("muc#roomconfig_enablelogging");
        if (field != null) {
            room.setLogEnabled( parseFirstValueAsBoolean( field, true ) );
        }

        field = completedForm.getField("x-muc#roomconfig_reservednick");
        if (field != null) {
            room.setLoginRestrictedToNickname( parseFirstValueAsBoolean( field, true ) );
        }

        field = completedForm.getField("x-muc#roomconfig_canchangenick");
        if (field != null) {
            room.setChangeNickname( parseFirstValueAsBoolean( field, true ) );
        }

        field = completedForm.getField("x-muc#roomconfig_registration");
        if (field != null) {
            room.setRegistrationEnabled( parseFirstValueAsBoolean( field, true ) );
        }

        // Update the modification date to reflect the last time when the room's configuration
        // was modified
        room.setModificationDate(new Date());

        if (room.isPersistent()) {
            room.saveToDB();
        }

        // Apply any configuration changes to the FMUC state.
        room.getFmucHandler().applyConfigurationChanges();

        // Set the new owners and admins of the room
        presences.addAll(room.addOwners(owners, senderRole));
        presences.addAll(room.addAdmins(admins, senderRole));

        if (ownersSent) {
            // Change the affiliation to "member" for the current owners that won't be neither
            // owner nor admin (if the form included the owners field)
            List<JID> ownersToRemove = new ArrayList<>(room.owners);
            ownersToRemove.removeAll(admins);
            ownersToRemove.removeAll(owners);
            for (JID jid : ownersToRemove) {
                // ignore group jids
                if (!GroupJID.isGroup(jid)) {
                    presences.addAll(room.addMember(jid, null, senderRole));
                }
            }
        }

        if (adminsSent) {
            // Change the affiliation to "member" for the current admins that won't be neither
            // owner nor admin (if the form included the admins field)
            List<JID> adminsToRemove = new ArrayList<>(room.admins);
            adminsToRemove.removeAll(admins);
            adminsToRemove.removeAll(owners);
            for (JID jid : adminsToRemove) {
                // ignore group jids
                if (!GroupJID.isGroup(jid)) {
                    presences.addAll(room.addMember(jid, null, senderRole));
                }
            }
        }

        // Destroy the room if the room is no longer persistent and there are no occupants in
        // the room
        if (!room.isPersistent() && room.getOccupantsCount() == 0) {
            room.destroyRoom(null, null);
        }

        // Send the updated presences to the room occupants
        for (Object presence : presences) {
            room.send((Presence) presence, room.getRole());
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

            field = configurationForm.getField("muc#roomconfig_allowpm");
            field.clearValues();
            field.addValue((room.canSendPrivateMessage() ));

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
            for (JID jid : room.getAdmins()) {
                if (GroupJID.isGroup(jid)) {
                    try {
                        // add each group member to the result (clients don't understand groups)
                        Group group = GroupManager.getInstance().getGroup(jid);
                        for (JID groupMember : group.getAll()) {
                            field.addValue(groupMember);
                        }
                    } catch (GroupNotFoundException gnfe) {
                        Log.warn("Invalid group JID in the member list: " + jid);
                    }
                } else {
                    field.addValue(jid.toString());
                }
            }

            field = configurationForm.getField("muc#roomconfig_roomowners");
            field.clearValues();
            for (JID jid : room.getOwners()) {
                if (GroupJID.isGroup(jid)) {
                    try {
                        // add each group member to the result (clients don't understand groups)
                        Group group = GroupManager.getInstance().getGroup(jid);
                        for (JID groupMember : group.getAll()) {
                            field.addValue(groupMember);
                        }
                    } catch (GroupNotFoundException gnfe) {
                        Log.warn("Invalid group JID in the member list: " + jid);
                    }
                } else {
                    field.addValue(jid.toString());
                }
            }

            // Remove the old element
            probeResult.remove(probeResult.element(QName.get("x", "jabber:x:data")));
            // Add the new representation of configurationForm as an element 
            probeResult.add(configurationForm.getElement());

        }
        finally {
            room.lock.readLock().unlock();
        }
    }

    private void init() {
        Element element = DocumentHelper.createElement(QName.get("query",
                "http://jabber.org/protocol/muc#owner"));

        configurationForm = new DataForm(DataForm.Type.form);
        configurationForm.setTitle(LocaleUtils.getLocalizedString("muc.form.conf.title"));
        List<String> params = new ArrayList<>();
        params.add(room.getName());
        configurationForm.addInstruction(LocaleUtils.getLocalizedString("muc.form.conf.instruction", params));

        configurationForm.addField("FORM_TYPE", null, Type.hidden)
                .addValue("http://jabber.org/protocol/muc#roomconfig");

        configurationForm.addField("muc#roomconfig_roomname", 
                LocaleUtils.getLocalizedString("muc.form.conf.owner_roomname"),
                Type.text_single);

        configurationForm.addField("muc#roomconfig_roomdesc",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_roomdesc"),
                Type.text_single);

        configurationForm.addField("muc#roomconfig_changesubject",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_changesubject"),
                Type.boolean_type);
        
        final FormField maxUsers = configurationForm.addField(
                "muc#roomconfig_maxusers",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_maxusers"),
                Type.list_single);
        maxUsers.addOption("10", "10");
        maxUsers.addOption("20", "20");
        maxUsers.addOption("30", "30");
        maxUsers.addOption("40", "40");
        maxUsers.addOption("50", "50");
        maxUsers.addOption(LocaleUtils.getLocalizedString("muc.form.conf.none"), "0");

        final FormField broadcast = configurationForm.addField(
                "muc#roomconfig_presencebroadcast",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_presencebroadcast"),
                Type.list_multi);
        broadcast.addOption(LocaleUtils.getLocalizedString("muc.form.conf.moderator"), "moderator");
        broadcast.addOption(LocaleUtils.getLocalizedString("muc.form.conf.participant"), "participant");
        broadcast.addOption(LocaleUtils.getLocalizedString("muc.form.conf.visitor"), "visitor");

        configurationForm.addField("muc#roomconfig_publicroom", 
                LocaleUtils.getLocalizedString("muc.form.conf.owner_publicroom"),
                Type.boolean_type);

        configurationForm.addField("muc#roomconfig_persistentroom",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_persistentroom"),
                Type.boolean_type);

        configurationForm.addField("muc#roomconfig_moderatedroom",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_moderatedroom"),
                Type.boolean_type);

        configurationForm.addField("muc#roomconfig_membersonly",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_membersonly"),
                Type.boolean_type);

        configurationForm.addField(null, null, Type.fixed)
                .addValue(LocaleUtils.getLocalizedString("muc.form.conf.allowinvitesfixed"));

        configurationForm.addField("muc#roomconfig_allowinvites",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_allowinvites"),
                Type.boolean_type);

        configurationForm.addField("muc#roomconfig_passwordprotectedroom",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_passwordprotectedroom"),
                Type.boolean_type);

        configurationForm.addField(null, null, Type.fixed)
                .addValue(LocaleUtils.getLocalizedString("muc.form.conf.roomsecretfixed"));

        configurationForm.addField("muc#roomconfig_roomsecret",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_roomsecret"),
                Type.text_private);
        
        final FormField whois = configurationForm.addField(
                "muc#roomconfig_whois",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_whois"),
                Type.list_single);
        whois.addOption(LocaleUtils.getLocalizedString("muc.form.conf.moderator"), "moderators");
        whois.addOption(LocaleUtils.getLocalizedString("muc.form.conf.anyone"), "anyone");

        final FormField allowpm = configurationForm.addField(
                "muc#roomconfig_allowpm",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_allowpm"),
                Type.list_single);
        allowpm.addOption(LocaleUtils.getLocalizedString("muc.form.conf.anyone"), "anyone");
        allowpm.addOption(LocaleUtils.getLocalizedString("muc.form.conf.moderator"), "moderators");
        allowpm.addOption(LocaleUtils.getLocalizedString("muc.form.conf.participant"), "participants");
        allowpm.addOption(LocaleUtils.getLocalizedString("muc.form.conf.none"), "none");

        configurationForm.addField("muc#roomconfig_enablelogging",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_enablelogging"),
                Type.boolean_type);

        configurationForm.addField("x-muc#roomconfig_reservednick",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_reservednick"),
                Type.boolean_type);

        configurationForm.addField("x-muc#roomconfig_canchangenick",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_canchangenick"),
                Type.boolean_type);

        configurationForm.addField(null, null, Type.fixed)
                .addValue(LocaleUtils.getLocalizedString("muc.form.conf.owner_registration"));

        configurationForm.addField("x-muc#roomconfig_registration",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_registration"),
                Type.boolean_type);

        configurationForm.addField(null, null, Type.fixed)
                .addValue(LocaleUtils.getLocalizedString("muc.form.conf.roomadminsfixed"));

        configurationForm.addField("muc#roomconfig_roomadmins",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_roomadmins"),
                Type.jid_multi);

        configurationForm.addField(null, null, Type.fixed)
                .addValue(LocaleUtils.getLocalizedString("muc.form.conf.roomownersfixed"));

        configurationForm.addField("muc#roomconfig_roomowners",
                LocaleUtils.getLocalizedString("muc.form.conf.owner_roomowners"),
                Type.jid_multi);

        // Create the probeResult and add the basic info together with the configuration form
        probeResult = element;
        probeResult.add(configurationForm.getElement());
    }

    /**
     * Returns the first value of the formfield as a boolean.
     *
     * @param field A form field (cannot be null)
     * @param defaultValue Returned if first value is null or empty.
     * @return true if the provided input equals '1' or 'true', false if the input equals '0' or 'false'.
     * @throws IllegalArgumentException when the input cannot be parsed as a boolean.
     * @deprecated Use FormField#parseFirstValueAsBoolean(String) provided by Tinder version 1.3.1 or newer.
     */
    @Deprecated
    public static boolean parseFirstValueAsBoolean( FormField field, boolean defaultValue )
    {
        final String value = field.getFirstValue();
        if ( value == null || value.isEmpty() )
        {
            return defaultValue;
        }
        if ( "0".equals( value ) || "false".equals( value ) )
        {
            return false;
        }
        if ( "1".equals( value ) || "true".equals( value ) )
        {
            return true;
        }

        throw new IllegalArgumentException( "Unable to parse value '" + value + "' as Data Form Field boolean." );
    }
}
