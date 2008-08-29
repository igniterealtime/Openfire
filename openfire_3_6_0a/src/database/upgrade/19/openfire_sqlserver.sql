/* Rename jiveUser to ofUser */
DROP INDEX jiveUser.jiveUser_cDate_idx;
ALTER TABLE jiveUser DROP CONSTRAINT jiveUser_pk;
sp_rename 'jiveUser', 'ofUser';
ALTER TABLE ofUser ADD CONSTRAINT ofUser_pk PRIMARY KEY (username);
CREATE INDEX ofUser_cDate_idx ON ofUser (creationDate ASC);

/* Rename jiveUserProp to ofUserProp */
ALTER TABLE jiveUserProp DROP CONSTRAINT jiveUserProp_pk;
sp_rename 'jiveUserProp', 'ofUserProp';
ALTER TABLE ofUserProp ADD CONSTRAINT ofUserProp_pk PRIMARY KEY (username, name);

/* Rename jiveUserFlag to ofUserFlag */
DROP INDEX jiveUserFlag.jiveUserFlag_sTime_idx;
DROP INDEX jiveUserFlag.jiveUserFlag_eTime_idx;
ALTER TABLE jiveUserFlag DROP CONSTRAINT jiveUserFlag_pk;
sp_rename 'jiveUserFlag', 'ofUserFlag';
ALTER TABLE ofUserFlag ADD CONSTRAINT ofUserFlag_pk PRIMARY KEY (username, name);
CREATE INDEX ofUserFlag_sTime_idx ON ofUserFlag (startTime ASC);
CREATE INDEX ofUserFlag_eTime_idx ON ofUserFlag (endTime ASC);

/* Rename jiveGroup to ofGroup */
ALTER TABLE jiveGroup DROP CONSTRAINT group_pk;
sp_rename 'jiveGroup', 'ofGroup';
ALTER TABLE ofGroup ADD CONSTRAINT ofGroup_pk PRIMARY KEY (groupName);

/* Rename jiveGroupProp to ofGroupProp */
ALTER TABLE jiveGroupProp DROP CONSTRAINT jiveGroupProp_pk;
sp_rename 'jiveGroupProp', 'ofGroupProp';
ALTER TABLE ofGroupProp ADD CONSTRAINT ofGroupProp_pk PRIMARY KEY (groupName, name);

/* Rename jiveGroupUser to ofGroupUser */
ALTER TABLE jiveGroupUser DROP CONSTRAINT jiveGroupUser_pk;
sp_rename 'jiveGroupUser', 'ofGroupUser';
ALTER TABLE ofGroupUser ADD CONSTRAINT ofGroupUser_pk PRIMARY KEY (groupName, username, administrator);

/* Rename jivePrivate to ofPrivate */
ALTER TABLE jivePrivate DROP CONSTRAINT JivePrivate_pk;
sp_rename 'jivePrivate', 'ofPrivate';
ALTER TABLE ofPrivate ADD CONSTRAINT ofPrivate_pk PRIMARY KEY (username, name, namespace);

/* Rename jiveOffline to ofOffline */
ALTER TABLE jiveOffline DROP CONSTRAINT jiveOffline_pk;
sp_rename 'jiveOffline', 'ofOffline';
ALTER TABLE ofOffline ADD CONSTRAINT ofOffline_pk PRIMARY KEY (username, messageID);

/* Rename jivePresence to ofPresence */
ALTER TABLE jivePresence DROP CONSTRAINT jivePresence_pk;
sp_rename 'jivePresence', 'ofPresence';
ALTER TABLE ofPresence ADD CONSTRAINT ofPresence_pk PRIMARY KEY (username);

/* Drop foreign key on jiveRoster from jiveRosterGroups */
ALTER TABLE jiveRosterGroups DROP CONSTRAINT jiveRosterGroups_rosterID_fk;

/* Rename jiveRoster to ofRoster */
DROP INDEX jiveRoster.jiveRoster_username_idx;
DROP INDEX jiveRoster.jiveRoster_jid_idx;
ALTER TABLE jiveRoster DROP CONSTRAINT jiveRoster_pk;
sp_rename 'jiveRoster', 'ofRoster';
ALTER TABLE ofRoster ADD CONSTRAINT ofRoster_pk PRIMARY KEY (rosterID);
CREATE INDEX ofRoster_username_idx ON ofRoster (username ASC);
CREATE INDEX ofRoster_jid_idx ON ofRoster (jid ASC);

/* Rename jiveRosterGroups to ofRosterGroups */
DROP INDEX jiveRosterGroups.jiveRosterGroups_rosterid_idx;
ALTER TABLE jiveRosterGroups DROP CONSTRAINT jiveRosterGroups_pk;
sp_rename 'jiveRosterGroups', 'ofRosterGroups';
ALTER TABLE ofRosterGroups ADD CONSTRAINT ofRosterGroups_pk PRIMARY KEY (rosterID, rank);
CREATE INDEX ofRosterGroups_rosterid_idx ON ofRosterGroups (rosterID ASC);

/* Readd foreign key */
ALTER TABLE ofRosterGroups ADD CONSTRAINT ofRosterGroups_rosterID_fk FOREIGN KEY (rosterID) REFERENCES ofRoster;

/* Rename jiveVCard to ofVCard */
ALTER TABLE jiveVCard DROP CONSTRAINT JiveVCard_pk;
sp_rename 'jiveVCard', 'ofVCard';
ALTER TABLE ofVCard ADD CONSTRAINT ofVCard_pk PRIMARY KEY (username);

/* Rename jiveID to ofID */
ALTER TABLE jiveID DROP CONSTRAINT jiveID_pk;
sp_rename 'jiveID', 'ofID';
ALTER TABLE ofID ADD CONSTRAINT ofID_pk PRIMARY KEY (idType);

/* Rename jiveProperty to ofProperty */
ALTER TABLE jiveProperty DROP CONSTRAINT jiveProperty_pk;
sp_rename 'jiveProperty', 'ofProperty';
ALTER TABLE ofProperty ADD CONSTRAINT ofProperty_pk PRIMARY KEY (name);

/* Rename jiveVersion to ofVersion */
ALTER TABLE jiveVersion DROP CONSTRAINT jiveVersion_pk;
sp_rename 'jiveVersion', 'ofVersion';
ALTER TABLE ofVersion ADD CONSTRAINT ofVersion_pk PRIMARY KEY (name);

/* Rename jiveExtComponentConf to ofExtComponentConf */
ALTER TABLE jiveExtComponentConf DROP CONSTRAINT jiveExtComponentConf_pk;
sp_rename 'jiveExtComponentConf', 'ofExtComponentConf';
ALTER TABLE ofExtComponentConf ADD CONSTRAINT ofExtComponentConf_pk PRIMARY KEY (subdomain);

/* Rename jiveRemoteServerConf to ofRemoteServerConf */
ALTER TABLE jiveRemoteServerConf DROP CONSTRAINT jiveRemoteServerConf_pk;
sp_rename 'jiveRemoteServerConf', 'ofRemoteServerConf';
ALTER TABLE ofRemoteServerConf ADD CONSTRAINT ofRemoteServerConf_pk PRIMARY KEY (xmppDomain);

/* Rename jivePrivacyList to ofPrivacyList */
DROP INDEX jivePrivacyList.jivePList_default_idx;
ALTER TABLE jivePrivacyList DROP CONSTRAINT jivePrivacyList_pk;
sp_rename 'jivePrivacyList', 'ofPrivacyList';
ALTER TABLE ofPrivacyList ADD CONSTRAINT ofPrivacyList_pk PRIMARY KEY (username, name);
CREATE INDEX ofPrivacyList_default_idx ON ofPrivacyList (username, isDefault);

/* Rename jiveSASLAuthorized to ofSASLAuthorized */
ALTER TABLE jiveSASLAuthorized DROP CONSTRAINT jiveSASLAuthoirzed_pk;
sp_rename 'jiveSASLAuthorized', 'ofSASLAuthorized';
ALTER TABLE ofSASLAuthorized ADD CONSTRAINT ofSASLAuthorized_pk PRIMARY KEY (username, principal);

/* Rename jiveSecurityAuditLog to ofSecurityAuditLog */
DROP INDEX jiveSecurityAuditLog.jiveSecAuditLog_tstamp_idx;
DROP INDEX jiveSecurityAuditLog.jiveSecAuditLog_uname_idx;
ALTER TABLE jiveSecurityAuditLog DROP CONSTRAINT jiveSecAuditLog_pk;
sp_rename 'jiveSecurityAuditLog', 'ofSecurityAuditLog';
ALTER TABLE ofSecurityAuditLog ADD CONSTRAINT ofSecurityAuditLog_pk PRIMARY KEY (msgID);
CREATE INDEX ofSecurityAuditLog_tstamp_idx ON ofSecurityAuditLog (entryStamp);
CREATE INDEX ofSecurityAuditLog_uname_idx ON ofSecurityAuditLog (username);

/* Rename mucService to ofMucService */
DROP INDEX mucService.mucService_serviceid_idx;
ALTER TABLE mucService DROP CONSTRAINT mucService_pk;
sp_rename 'mucService', 'ofMucService';
ALTER TABLE ofMucService ADD CONSTRAINT ofMucService_pk PRIMARY KEY (subdomain);
CREATE INDEX ofMucService_serviceid_idx ON ofMucService(serviceID);

/* Rename mucServiceProp to ofMucServiceProp */
ALTER TABLE mucServiceProp DROP CONSTRAINT mucServiceProp_pk;
sp_rename 'mucServiceProp', 'ofMucServiceProp';
ALTER TABLE ofMucServiceProp ADD CONSTRAINT ofMucServiceProp_pk PRIMARY KEY (serviceID, name);

/* Rename mucRoom to ofMucRoom */
DROP INDEX mucRoom.mucRoom_roomID_idx;
DROP INDEX mucRoom.mucRoom_serviceID_idx;
ALTER TABLE mucRoom DROP CONSTRAINT mucRoom__pk;
sp_rename 'mucRoom', 'ofMucRoom';
ALTER TABLE ofMucRoom ADD CONSTRAINT ofMucRoom_pk PRIMARY KEY (serviceID, name);
CREATE INDEX ofMucRoom_roomid_idx on ofMucRoom(roomID);
CREATE INDEX ofMucRoom_serviceid_idx on ofMucRoom(serviceID);

/* Rename mucRoomProp to ofMucRoomProp */
ALTER TABLE mucRoomProp DROP CONSTRAINT mucRoomProp_pk;
sp_rename 'mucRoomProp', 'ofMucRoomProp';
ALTER TABLE ofMucRoomProp ADD CONSTRAINT ofMucRoomProp_pk PRIMARY KEY (roomID, name);

/* Rename mucAffiliation to ofMucAffiliation */
ALTER TABLE mucAffiliation DROP CONSTRAINT mucAffiliation__pk;
sp_rename 'mucAffiliation', 'ofMucAffiliation';
ALTER TABLE ofMucAffiliation ADD CONSTRAINT ofMucAffiliation__pk PRIMARY KEY (roomID,jid);

/* Rename mucMember to ofMucMember */
ALTER TABLE mucMember DROP CONSTRAINT mucMember__pk;
sp_rename 'mucMember', 'ofMucMember';
ALTER TABLE ofMucMember ADD CONSTRAINT ofMucMember_pk PRIMARY KEY (roomID,jid);

/* Rename mucConversationLog to ofMucConversationLog */
DROP INDEX mucConversationLog.mucLog_time_idx;
sp_rename 'mucConversationLog', 'ofMucConversationLog';
CREATE INDEX ofMucConversationLog_time_idx ON ofMucConversationLog (logTime);

/* Rename pubsubNode to ofPubsubNode */
ALTER TABLE pubsubNode DROP CONSTRAINT pubsubNode_pk;
sp_rename 'pubsubNode', 'ofPubsubNode';
ALTER TABLE ofPubsubNode ADD CONSTRAINT ofPubsubNode_pk PRIMARY KEY (serviceID, nodeID);

/* Rename pubsubNodeJIDs to ofPubsubNodeJIDs */
ALTER TABLE pubsubNodeJIDs DROP CONSTRAINT pubsubJID_pk;
sp_rename 'pubsubNodeJIDs', 'ofPubsubNodeJIDs';
ALTER TABLE ofPubsubNodeJIDs ADD CONSTRAINT ofPubsubNodeJIDs_pk PRIMARY KEY (serviceID, nodeID, jid);

/* Rename pubsubNodeGroups to ofPubsubNodeGroups */
DROP INDEX pubsubNodeGroups.pubsubNodeGroups_idx;
sp_rename 'pubsubNodeGroups', 'ofPubsubNodeGroups';
CREATE INDEX ofPubsubNodeGroups_idx ON ofPubsubNodeGroups (serviceID, nodeID);

/* Rename pubsubAffiliation to ofPubsubAffiliation */
ALTER TABLE pubsubAffiliation DROP CONSTRAINT pubsubAffil_pk;
sp_rename 'pubsubAffiliation', 'ofPubsubAffiliation';
ALTER TABLE ofPubsubAffiliation ADD CONSTRAINT ofPubsubAffiliation_pk PRIMARY KEY (serviceID, nodeID, jid);

/* Rename pubsubItem to ofPubsubItem */
ALTER TABLE pubsubItem DROP CONSTRAINT pubsubItem_pk;
sp_rename 'pubsubItem', 'ofPubsubItem';
ALTER TABLE ofPubsubItem ADD CONSTRAINT ofPubsubItem_pk PRIMARY KEY (serviceID, nodeID, id);

/* Rename pubsubSubscription to ofPubsubSubscription */
ALTER TABLE pubsubSubscription DROP CONSTRAINT pubsubSubs_pk;
sp_rename 'pubsubSubscription', 'ofPubsubSubscription';
ALTER TABLE ofPubsubSubscription ADD CONSTRAINT ofPubsubSubscription_pk PRIMARY KEY (serviceID, nodeID, id);

/* Rename pubsubDefaultConf to ofPubsubDefaultConf */
ALTER TABLE pubsubDefaultConf DROP CONSTRAINT pubsubDefConf_pk;
sp_rename 'pubsubDefaultConf', 'ofPubsubDefaultConf';
ALTER TABLE ofPubsubDefaultConf ADD CONSTRAINT ofPubsubDefaultConf_pk PRIMARY KEY (serviceID, leaf);

/* Update version */
UPDATE ofVersion SET version = 19 WHERE name = 'openfire';
