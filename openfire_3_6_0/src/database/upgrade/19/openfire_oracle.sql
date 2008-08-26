-- Rename jiveUser to ofUser
DROP INDEX jiveUser_cDate_idx;
ALTER TABLE jiveUser DROP CONSTRAINT jiveUser_pk;
ALTER TABLE jiveUser RENAME TO ofUser;
ALTER TABLE ofUser ADD CONSTRAINT ofUser_pk PRIMARY KEY (username);
CREATE INDEX ofUser_cDate_idx ON ofUser (creationDate ASC);

-- Rename jiveUserProp to ofUserProp
ALTER TABLE jiveUserProp DROP CONSTRAINT jiveUserProp_pk;
ALTER TABLE jiveUserProp RENAME TO ofUserProp;
ALTER TABLE ofUserProp ADD CONSTRAINT ofUserProp_pk PRIMARY KEY (username, name);

-- Rename jiveUserFlag to ofUserFlag
DROP INDEX jiveUserFlag_sTime_idx;
DROP INDEX jiveUserFlag_eTime_idx;
ALTER TABLE jiveUserFlag DROP CONSTRAINT jiveUserFlag_pk;
ALTER TABLE jiveUserFlag RENAME TO ofUserFlag;
ALTER TABLE ofUserFlag ADD CONSTRAINT ofUserFlag_pk PRIMARY KEY (username, name);
CREATE INDEX ofUserFlag_sTime_idx ON ofUserFlag (startTime ASC);
CREATE INDEX ofUserFlag_eTime_idx ON ofUserFlag (endTime ASC);

-- Rename jiveGroup to ofGroup
ALTER TABLE jiveGroup DROP CONSTRAINT jiveGroup_pk;
ALTER TABLE jiveGroup RENAME TO ofGroup;
ALTER TABLE ofGroup ADD CONSTRAINT ofGroup_pk PRIMARY KEY (groupName);

-- Rename jiveGroupProp to ofGroupProp
ALTER TABLE jiveGroupProp DROP CONSTRAINT jiveGroupProp_pk;
ALTER TABLE jiveGroupProp RENAME TO ofGroupProp;
ALTER TABLE ofGroupProp ADD CONSTRAINT ofGroupProp_pk PRIMARY KEY (groupName, name);

-- Rename jiveGroupUser to ofGroupUser
ALTER TABLE jiveGroupUser DROP CONSTRAINT jiveGroupUser;
ALTER TABLE jiveGroupUser RENAME TO ofGroupUser;
ALTER TABLE ofGroupUser ADD CONSTRAINT ofGroupUser_pk PRIMARY KEY (groupName, username, administrator);

-- Rename jivePrivate to ofPrivate
ALTER TABLE jivePrivate DROP CONSTRAINT jivePrivate_pk;
ALTER TABLE jivePrivate RENAME TO ofPrivate;
ALTER TABLE ofPrivate ADD CONSTRAINT ofPrivate_pk PRIMARY KEY (username, name, namespace);

-- Rename jiveOffline to ofOffline
ALTER TABLE jiveOffline DROP CONSTRAINT jiveOffline_pk;
ALTER TABLE jiveOffline RENAME TO ofOffline;
ALTER TABLE ofOffline ADD CONSTRAINT ofOffline_pk PRIMARY KEY (username, messageID);

-- Rename jivePresence to ofPresence
ALTER TABLE jivePresence DROP CONSTRAINT jivePresence_pk;
ALTER TABLE jivePresence RENAME TO ofPresence;
ALTER TABLE ofPresence ADD CONSTRAINT ofPresence_pk PRIMARY KEY (username);

-- Drop foreign key on jiveRoster from jiveRosterGroups
ALTER TABLE jiveRosterGroups DROP CONSTRAINT jiveRosterGroups_rosterID_fk;

-- Rename jiveRoster to ofRoster
DROP INDEX jiveRoster_username_idx;
DROP INDEX jiveRoster_jid_idx;
ALTER TABLE jiveRoster DROP CONSTRAINT jiveRoster_pk;
ALTER TABLE jiveRoster RENAME TO ofRoster;
ALTER TABLE ofRoster ADD CONSTRAINT ofRoster_pk PRIMARY KEY (rosterID);
CREATE INDEX ofRoster_username_idx ON ofRoster (username ASC);
CREATE INDEX ofRoster_jid_idx ON ofRoster (jid ASC);

-- Rename jiveRosterGroups to ofRosterGroups
DROP INDEX jiveRosterGroup_rosterid_idx;
ALTER TABLE jiveRosterGroups DROP CONSTRAINT jiveRosterGroups_pk;
ALTER TABLE jiveRosterGroups RENAME TO ofRosterGroups;
ALTER TABLE ofRosterGroups ADD CONSTRAINT ofRosterGroups_pk PRIMARY KEY (rosterID, rank);
CREATE INDEX ofRosterGroup_rosterid_idx ON ofRosterGroups (rosterID ASC);

-- Readd foreign key
ALTER TABLE ofRosterGroups ADD CONSTRAINT ofRosterGroups_rosterID_fk FOREIGN KEY (rosterID) REFERENCES ofRoster INITIALLY DEFERRED DEFERRABLE;

-- Rename jiveVCard to ofVCard
ALTER TABLE jiveVCard DROP CONSTRAINT JiveVCard_pk;
ALTER TABLE jiveVCard RENAME TO ofVCard;
ALTER TABLE ofVCard ADD CONSTRAINT ofVCard_pk PRIMARY KEY (username);

-- Rename jiveID to ofID
ALTER TABLE jiveID DROP CONSTRAINT jiveID_pk;
ALTER TABLE jiveID RENAME TO ofID;
ALTER TABLE ofID ADD CONSTRAINT ofID_pk PRIMARY KEY (idType);

-- Rename jiveProperty to ofProperty
ALTER TABLE jiveProperty DROP CONSTRAINT jiveProperty_pk;
ALTER TABLE jiveProperty RENAME TO ofProperty;
ALTER TABLE ofProperty ADD CONSTRAINT ofProperty_pk PRIMARY KEY (name);

-- Rename jiveVersion to ofVersion
ALTER TABLE jiveVersion DROP CONSTRAINT jiveVersion_pk;
ALTER TABLE jiveVersion RENAME TO ofVersion;
ALTER TABLE ofVersion ADD CONSTRAINT ofVersion_pk PRIMARY KEY (name);

-- Rename jiveExtComponentConf to ofExtComponentConf
ALTER TABLE jiveExtComponentConf DROP CONSTRAINT jiveExtComponentConf_pk;
ALTER TABLE jiveExtComponentConf RENAME TO ofExtComponentConf;
ALTER TABLE ofExtComponentConf ADD CONSTRAINT ofExtComponentConf_pk PRIMARY KEY (subdomain);

-- Rename jiveRemoteServerConf to ofRemoteServerConf
ALTER TABLE jiveRemoteServerConf DROP CONSTRAINT jiveRemoteServerConf_pk;
ALTER TABLE jiveRemoteServerConf RENAME TO ofRemoteServerConf;
ALTER TABLE ofRemoteServerConf ADD CONSTRAINT ofRemoteServerConf_pk PRIMARY KEY (xmppDomain);

-- Rename jivePrivacyList to ofPrivacyList
DROP INDEX jivePList_default_idx;
ALTER TABLE jivePrivacyList DROP CONSTRAINT jivePrivacyList_pk;
ALTER TABLE jivePrivacyList RENAME TO ofPrivacyList;
ALTER TABLE ofPrivacyList ADD CONSTRAINT ofPrivacyList_pk PRIMARY KEY (username, name);
CREATE INDEX ofPrivacyList_default_idx ON ofPrivacyList (username, isDefault);

-- Rename jiveSASLAuthorized to ofSASLAuthorized
ALTER TABLE jiveSASLAuthorized DROP CONSTRAINT jiveSASLAuthoirzed_pk;
ALTER TABLE jiveSASLAuthorized RENAME TO ofSASLAuthorized;
ALTER TABLE ofSASLAuthorized ADD CONSTRAINT ofSASLAuthorized_pk PRIMARY KEY (username, principal);

-- Rename jiveSecurityAuditLog to ofSecurityAuditLog
DROP INDEX jiveSecAuditLog_tstamp_idx;
DROP INDEX jiveSecAuditLog_uname_idx;
ALTER TABLE jiveSecurityAuditLog DROP CONSTRAINT jiveSecAuditLog_pk;
ALTER TABLE jiveSecurityAuditLog RENAME TO ofSecurityAuditLog;
ALTER TABLE ofSecurityAuditLog ADD CONSTRAINT ofSecurityAuditLog_pk PRIMARY KEY (msgID);
CREATE INDEX ofSecurityAuditLog_tstamp_idx ON ofSecurityAuditLog (entryStamp);
CREATE INDEX ofSecurityAuditLog_uname_idx ON ofSecurityAuditLog (username);

-- Rename mucService to ofMucService
DROP INDEX mucService_serviceid_idx;
ALTER TABLE mucService DROP CONSTRAINT mucService_pk;
ALTER TABLE mucService RENAME TO ofMucService;
ALTER TABLE ofMucService ADD CONSTRAINT ofMucService_pk PRIMARY KEY (subdomain);
CREATE INDEX ofMucService_serviceid_idx ON ofMucService(serviceID);

-- Rename mucServiceProp to ofMucServiceProp
ALTER TABLE mucServiceProp DROP CONSTRAINT mucServiceProp_pk;
ALTER TABLE mucServiceProp RENAME TO ofMucServiceProp;
ALTER TABLE ofMucServiceProp ADD CONSTRAINT ofMucServiceProp_pk PRIMARY KEY (serviceID, name);

-- Rename mucRoom to ofMucRoom
DROP INDEX mucRoom_roomid_idx;
DROP INDEX mucRm_serviceid_idx;
ALTER TABLE mucRoom DROP CONSTRAINT mucRoom_pk;
ALTER TABLE mucRoom RENAME TO ofMucRoom;
ALTER TABLE ofMucRoom ADD CONSTRAINT ofMucRoom_pk PRIMARY KEY (serviceID, name);
CREATE INDEX ofMucRoom_roomid_idx ON ofMucRoom (roomID);
CREATE INDEX ofMucRoom_serviceid_idx ON ofMucRoom (serviceID);

-- Rename mucRoomProp to ofMucRoomProp
ALTER TABLE mucRoomProp DROP CONSTRAINT mucRoomProp_pk;
ALTER TABLE mucRoomProp RENAME TO ofMucRoomProp;
ALTER TABLE ofMucRoomProp ADD CONSTRAINT ofMucRoomProp_pk PRIMARY KEY (roomID, name);

-- Rename mucAffiliation to ofMucAffiliation
ALTER TABLE mucAffiliation DROP CONSTRAINT mucAffiliation_pk;
ALTER TABLE mucAffiliation RENAME TO ofMucAffiliation;
ALTER TABLE ofMucAffiliation ADD CONSTRAINT ofMucAffiliation_pk PRIMARY KEY (roomID, jid);

-- Rename mucMember to ofMucMember
ALTER TABLE mucMember DROP CONSTRAINT mucMember_pk;
ALTER TABLE mucMember RENAME TO ofMucMember;
ALTER TABLE ofMucMember ADD CONSTRAINT ofMucMember_pk PRIMARY KEY (roomID, jid);

-- Rename mucConversationLog to ofMucConversationLog
DROP INDEX mucLog_time_idx;
ALTER TABLE mucConversationLog RENAME TO ofMucConversationLog;
CREATE INDEX ofMucConversationLog_time_idx ON ofMucConversationLog (logTime);

-- Rename pubsubNode to ofPubsubNode
ALTER TABLE pubsubNode DROP CONSTRAINT pubsubNode_pk;
ALTER TABLE pubsubNode RENAME TO ofPubsubNode;
ALTER TABLE ofPubsubNode ADD CONSTRAINT ofPubsubNode_pk PRIMARY KEY (serviceID, nodeID);

-- Rename pubsubNodeJIDs to ofPubsubNodeJIDs
ALTER TABLE pubsubNodeJIDs DROP CONSTRAINT pubsubJID_pk;
ALTER TABLE pubsubNodeJIDs RENAME TO ofPubsubNodeJIDs;
ALTER TABLE ofPubsubNodeJIDs ADD CONSTRAINT ofPubsubNodeJIDs_pk PRIMARY KEY (serviceID, nodeID, jid);

-- Rename pubsubNodeGroups to ofPubsubNodeGroups
DROP INDEX pubsubNodeGroups_idx;
ALTER TABLE pubsubNodeGroups RENAME TO ofPubsubNodeGroups;
CREATE INDEX ofPubsubNodeGroups_idx ON ofPubsubNodeGroups (serviceID, nodeID);

-- Rename pubsubAffiliation to ofPubsubAffiliation
ALTER TABLE pubsubAffiliation DROP CONSTRAINT pubsubAffil_pk;
ALTER TABLE pubsubAffiliation RENAME TO ofPubsubAffiliation;
ALTER TABLE ofPubsubAffiliation ADD CONSTRAINT ofPubsubAffiliation_pk PRIMARY KEY (serviceID, nodeID, jid);

-- Rename pubsubItem to ofPubsubItem
ALTER TABLE pubsubItem DROP CONSTRAINT pubsubItem_pk;
ALTER TABLE pubsubItem RENAME TO ofPubsubItem;
ALTER TABLE ofPubsubItem ADD CONSTRAINT ofPubsubItem_pk PRIMARY KEY (serviceID, nodeID, id);

-- Rename pubsubSubscription to ofPubsubSubscription
ALTER TABLE pubsubSubscription DROP CONSTRAINT pubsubSubs_pk;
ALTER TABLE pubsubSubscription RENAME TO ofPubsubSubscription;
ALTER TABLE ofPubsubSubscription ADD CONSTRAINT ofPubsubSubscription_pk PRIMARY KEY (serviceID, nodeID, id);

-- Rename pubsubDefaultConf to ofPubsubDefaultConf
ALTER TABLE pubsubDefaultConf DROP CONSTRAINT pubsubDefConf_pk;
ALTER TABLE pubsubDefaultConf RENAME TO ofPubsubDefaultConf;
ALTER TABLE ofPubsubDefaultConf ADD CONSTRAINT ofPubsubDefaultConf_pk PRIMARY KEY (serviceID, leaf);

-- Update version
UPDATE ofVersion SET version = 19 WHERE name = 'openfire';

COMMIT;