package org.jivesoftware.openfire.entity;

import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "chatRoom")
@XmlType(propOrder = { "roomName", "naturalName", "description", "password", "subject", "creationDate",
        "modificationDate", "maxUsers", "persistent", "publicRoom", "registrationEnabled", "canAnyoneDiscoverJID",
        "canOccupantsChangeSubject", "canOccupantsInvite", "canChangeNickname", "logEnabled",
        "loginRestrictedToNickname", "membersOnly", "moderated", "broadcastPresenceRoles", "owners", "admins",
        "members", "outcasts" })
public class MUCRoomEntity {

    private String roomName;
    private String description;
    private String password;
    private String subject;
    private String naturalName;

    private int maxUsers;

    private Date creationDate;
    private Date modificationDate;

    private boolean persistent;
    private boolean publicRoom;
    private boolean registrationEnabled;
    private boolean canAnyoneDiscoverJID;
    private boolean canOccupantsChangeSubject;
    private boolean canOccupantsInvite;
    private boolean canChangeNickname;
    private boolean logEnabled;
    private boolean loginRestrictedToNickname;
    private boolean membersOnly;
    private boolean moderated;

    private List<String> broadcastPresenceRoles;

    private List<String> owners;

    private List<String> admins;

    private List<String> members;

    private List<String> outcasts;

    public MUCRoomEntity() {
    }

    public MUCRoomEntity(String naturalName, String roomName, String description) {
        this.naturalName = naturalName;
        this.roomName = roomName;
        this.description = description;
    }

    @XmlElement
    public String getNaturalName() {
        return naturalName;
    }

    public void setNaturalName(String naturalName) {
        this.naturalName = naturalName;
    }

    @XmlElement
    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @XmlElement
    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    @XmlElement
    public int getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(int maxUsers) {
        this.maxUsers = maxUsers;
    }

    @XmlElement
    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    @XmlElement
    public Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(Date modificationDate) {
        this.modificationDate = modificationDate;
    }

    @XmlElement
    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    @XmlElement
    public boolean isPublicRoom() {
        return publicRoom;
    }

    public void setPublicRoom(boolean publicRoom) {
        this.publicRoom = publicRoom;
    }

    @XmlElement
    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    @XmlElement
    public boolean isCanAnyoneDiscoverJID() {
        return canAnyoneDiscoverJID;
    }

    public void setCanAnyoneDiscoverJID(boolean canAnyoneDiscoverJID) {
        this.canAnyoneDiscoverJID = canAnyoneDiscoverJID;
    }

    @XmlElement
    public boolean isCanOccupantsChangeSubject() {
        return canOccupantsChangeSubject;
    }

    public void setCanOccupantsChangeSubject(boolean canOccupantsChangeSubject) {
        this.canOccupantsChangeSubject = canOccupantsChangeSubject;
    }

    @XmlElement
    public boolean isCanOccupantsInvite() {
        return canOccupantsInvite;
    }

    public void setCanOccupantsInvite(boolean canOccupantsInvite) {
        this.canOccupantsInvite = canOccupantsInvite;
    }

    public void setBroadcastPresenceRoles(List<String> broadcastPresenceRoles) {
        this.broadcastPresenceRoles = broadcastPresenceRoles;
    }

    @XmlElement
    public boolean isCanChangeNickname() {
        return canChangeNickname;
    }

    public void setCanChangeNickname(boolean canChangeNickname) {
        this.canChangeNickname = canChangeNickname;
    }

    @XmlElement
    public boolean isLogEnabled() {
        return logEnabled;
    }

    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    @XmlElement
    public boolean isLoginRestrictedToNickname() {
        return loginRestrictedToNickname;
    }

    public void setLoginRestrictedToNickname(boolean loginRestrictedToNickname) {
        this.loginRestrictedToNickname = loginRestrictedToNickname;
    }

    @XmlElement
    public boolean isMembersOnly() {
        return membersOnly;
    }

    public void setMembersOnly(boolean membersOnly) {
        this.membersOnly = membersOnly;
    }

    @XmlElement
    public boolean isModerated() {
        return moderated;
    }

    public void setModerated(boolean moderated) {
        this.moderated = moderated;
    }

    @XmlElement(name = "broadcastPresenceRole")
    @XmlElementWrapper(name = "broadcastPresenceRoles")
    public List<String> getBroadcastPresenceRoles() {
        return broadcastPresenceRoles;
    }

    @XmlElementWrapper(name = "owners")
    @XmlElement(name = "owner")
    public List<String> getOwners() {
        return owners;
    }

    public void setOwners(List<String> owners) {
        this.owners = owners;
    }

    @XmlElementWrapper(name = "members")
    @XmlElement(name = "member")
    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }

    @XmlElementWrapper(name = "outcasts")
    @XmlElement(name = "outcast")
    public List<String> getOutcasts() {
        return outcasts;
    }

    public void setOutcasts(List<String> outcasts) {
        this.outcasts = outcasts;
    }

    @XmlElementWrapper(name = "admins")
    @XmlElement(name = "admin")
    public List<String> getAdmins() {
        return admins;
    }

    public void setAdmins(List<String> admins) {
        this.admins = admins;
    }

}
