-- Rename jiveUser to ofUser
DROP INDEX jiveUsr_cDate_idx;
ALTER TABLE jiveUser DROP CONSTRAINT jiveUser_pk;
RENAME jiveUser TO ofUser;
ALTER TABLE ofUser ADD CONSTRAINT ofUser_pk PRIMARY KEY (username);
CREATE INDEX ofUser_cDate_idx ON ofUser (creationDate ASC);

-- Rename jiveUserProp to ofUserProp
ALTER TABLE jiveUserProp DROP CONSTRAINT jiveUsrProp_pk;
RENAME jiveUserProp TO ofUserProp;
ALTER TABLE ofUserProp ADD CONSTRAINT ofUserProp_pk PRIMARY KEY (username, name);

-- Rename jiveUserFlag to ofUserFlag
DROP INDEX jiveUserFlag_sTime_idx;
DROP INDEX jiveUserFlag_eTime_idx;
ALTER TABLE jiveUserFlag DROP CONSTRAINT jiveUserFlag_pk;
RENAME jiveUserFlag TO ofUserFlag;
ALTER TABLE ofUserFlag ADD CONSTRAINT ofUserFlag_pk PRIMARY KEY (username, name);
CREATE INDEX ofUserFlag_sTime_idx ON ofUserFlag (startTime ASC);
CREATE INDEX ofUserFlag_eTime_idx ON ofUserFlag (endTime ASC);

-- Rename jiveGroup to ofGroup
ALTER TABLE jiveGroup DROP CONSTRAINT jiveGroup_pk;
RENAME jiveGroup TO ofGroup;
ALTER TABLE ofGroup ADD CONSTRAINT ofGroup_pk PRIMARY KEY (groupName);

-- Rename jiveGroupProp to ofGroupProp
ALTER TABLE jiveGroupProp DROP CONSTRAINT jiveGrpProp_pk;
RENAME jiveGroupProp TO ofGroupProp;
ALTER TABLE ofGroupProp ADD CONSTRAINT ofGroupProp_pk PRIMARY KEY (groupName, name);

-- Rename jiveGroupUser to ofGroupUser
ALTER TABLE jiveGroupUser DROP CONSTRAINT jiveGrpUser;
RENAME jiveGroupUser TO ofGroupUser;
ALTER TABLE ofGroupUser ADD CONSTRAINT ofGroupUser_pk PRIMARY KEY (groupName, username, administrator);

-- Rename jivePrivate to ofPrivate
ALTER TABLE jivePrivate DROP CONSTRAINT jivePrivate_pk;
RENAME jivePrivate TO ofPrivate;
ALTER TABLE ofPrivate ADD CONSTRAINT ofPrivate_pk PRIMARY KEY (username, name, namespace);

-- Rename jiveOffline to ofOffline
ALTER TABLE jiveOffline DROP CONSTRAINT jiveOffline_pk;
RENAME jiveOffline TO ofOffline;
ALTER TABLE ofOffline ADD CONSTRAINT ofOffline_pk PRIMARY KEY (username, messageID);

-- Rename jivePresence to ofPresence
ALTER TABLE jivePresence DROP CONSTRAINT jivePresence_pk;
RENAME jivePresence TO ofPresence;
ALTER TABLE ofPresence ADD CONSTRAINT ofPresence_pk PRIMARY KEY (username);

-- Rename jiveRoster to ofRoster
DROP INDEX jiveR_userid_idx;
DROP INDEX jiveR_jid_idx;
ALTER TABLE jiveRoster DROP CONSTRAINT jiveRoster_pk;
RENAME jiveRoster TO ofRoster;
ALTER TABLE ofRoster ADD CONSTRAINT ofRoster_pk PRIMARY KEY (rosterID);
CREATE INDEX ofRoster_username_idx ON ofRoster (username ASC);
CREATE INDEX ofRoster_jid_idx ON ofRoster (jid ASC);

-- Rename jiveRosterGroups to ofRosterGroups
DROP INDEX jiveRoGrps_rid_idx;
ALTER TABLE jiveRosterGroups DROP CONSTRAINT jiveRoGrps_pk;
RENAME jiveRosterGroups TO ofRosterGroups;
ALTER TABLE ofRosterGroups ADD CONSTRAINT ofRosterGroups_pk PRIMARY KEY (rosterID, rank);
CREATE INDEX ofRosterGroups_rosterid_idx ON ofRosterGroups (rosterID ASC);

-- Rename jiveVCard to ofVCard
ALTER TABLE jiveVCard DROP CONSTRAINT jiveVCard_pk;
RENAME jiveVCard TO ofVCard;
ALTER TABLE ofVCard ADD CONSTRAINT ofVCard_pk PRIMARY KEY (username);

-- Rename jiveID to ofID
ALTER TABLE jiveID DROP CONSTRAINT jiveID_pk;
RENAME jiveID TO ofID;
ALTER TABLE ofID ADD CONSTRAINT ofID_pk PRIMARY KEY (idType);

-- Rename jiveProperty to ofProperty
ALTER TABLE jiveProperty DROP CONSTRAINT jiveProperty_pk;
RENAME jiveProperty TO ofProperty;
ALTER TABLE ofProperty ADD CONSTRAINT ofProperty_pk PRIMARY KEY (name);

-- Rename jiveVersion to ofVersion
ALTER TABLE jiveVersion DROP CONSTRAINT jiveVersion_pk;
RENAME jiveVersion TO ofVersion;
ALTER TABLE ofVersion ADD CONSTRAINT ofVersion_pk PRIMARY KEY (name);

-- Rename jiveExtComponentConf to ofExtComponentConf
ALTER TABLE jiveExtComponentConf DROP CONSTRAINT jiveExtCmpConf_pk;
RENAME jiveExtComponentConf TO ofExtComponentConf;
ALTER TABLE ofExtComponentConf ADD CONSTRAINT ofExtCmpConf_pk PRIMARY KEY (subdomain);

-- Rename jiveRemoteServerConf to ofRemoteServerConf
ALTER TABLE jiveRemoteServerConf DROP CONSTRAINT jiveRmSrvConf_pk;
RENAME jiveRemoteServerConf TO ofRemoteServerConf;
ALTER TABLE ofRemoteServerConf ADD CONSTRAINT ofRmSrvConf_pk PRIMARY KEY (xmppDomain);

-- Rename jivePrivacyList to ofPrivacyList
DROP INDEX jList_default_idx;
ALTER TABLE jivePrivacyList DROP CONSTRAINT jivePrivacyList_pk;
RENAME jivePrivacyList TO ofPrivacyList;
ALTER TABLE ofPrivacyList ADD CONSTRAINT ofPrivacyList_pk PRIMARY KEY (username, name);
CREATE INDEX ofPrivacyList_default_idx ON ofPrivacyList (username, isDefault);

-- Rename jiveSASLAuthorized to ofSASLAuthorized
ALTER TABLE jiveSASLAuthorized DROP CONSTRAINT jSASLAuthrizd_pk;
RENAME jiveSASLAuthorized TO ofSASLAuthorized;
ALTER TABLE ofSASLAuthorized ADD CONSTRAINT ofSASLAuthrizd_pk PRIMARY KEY (username, principal);

-- Rename jiveSecurityAuditLog to ofSecurityAuditLog
DROP INDEX jiveSecAuditLog_tstamp_idx;
DROP INDEX jiveSecAuditLog_uname_idx;
ALTER TABLE jiveSecurityAuditLog DROP CONSTRAINT jiveSecAuditLog_pk;
RENAME jiveSecurityAuditLog TO ofSecurityAuditLog;
ALTER TABLE ofSecurityAuditLog ADD CONSTRAINT ofSecAuditLog_pk PRIMARY KEY (msgID);
CREATE INDEX ofSecAuditLog_tstamp_idx ON ofSecurityAuditLog (entryStamp);
CREATE INDEX ofSecAuditLog_uname_idx ON ofSecurityAuditLog (username);

-- Rename mucService to ofMucService
DROP INDEX mucService_serviceid_idx;
ALTER TABLE mucService DROP CONSTRAINT mucService_pk;
RENAME mucService TO ofMucService;
ALTER TABLE ofMucService ADD CONSTRAINT ofMucService_pk PRIMARY KEY (subdomain);
CREATE INDEX ofMucService_serviceid_idx ON ofMucService(serviceID);

-- Rename mucServiceProp to ofMucServiceProp
ALTER TABLE mucServiceProp DROP CONSTRAINT mucServiceProp_pk;
RENAME mucServiceProp TO ofMucServiceProp;
ALTER TABLE ofMucServiceProp ADD CONSTRAINT ofMucSrvProp_pk PRIMARY KEY (serviceID, name);

-- Rename mucRoom to ofMucRoom
DROP INDEX mucRm_roomid_idx;
DROP INDEX mucRm_serviceid_idx;
ALTER TABLE mucRoom DROP CONSTRAINT mucRoom_pk;
RENAME mucRoom TO ofMucRoom;
ALTER TABLE ofMucRoom ADD CONSTRAINT ofMucRoom_pk PRIMARY KEY (serviceID, name);
CREATE INDEX ofMucRoom_roomid_idx ON ofMucRoom (roomID);
CREATE INDEX ofMucRoom_srvid_idx ON ofMucRoom (serviceID);

-- Rename mucRoomProp to ofMucRoomProp
ALTER TABLE mucRoomProp DROP CONSTRAINT mucRoomProp_pk;
RENAME mucRoomProp TO ofMucRoomProp;
ALTER TABLE ofMucRoomProp ADD CONSTRAINT ofMucRoomProp_pk PRIMARY KEY (roomID, name);

-- Rename mucAffiliation to ofMucAffiliation
ALTER TABLE mucAffiliation DROP CONSTRAINT mucAffiliation_pk;
RENAME mucAffiliation TO ofMucAffiliation;
ALTER TABLE ofMucAffiliation ADD CONSTRAINT ofMucAffil_pk PRIMARY KEY (roomID, jid);

-- Rename mucMember to ofMucMember
ALTER TABLE mucMember DROP CONSTRAINT mucMember_pk;
RENAME mucMember TO ofMucMember;
ALTER TABLE ofMucMember ADD CONSTRAINT ofMucMember_pk PRIMARY KEY (roomID, jid);

-- Rename mucConversationLog to ofMucConversationLog
--Past scripts make it impossible to know if this is logtime or time, sigh:
--DROP INDEX mucLog_time_idx;
RENAME mucConversationLog TO ofMucConversationLog;
CREATE INDEX ofMucConvLog_time_idx ON ofMucConversationLog (logTime);

-- Rename pubsubNode to ofPubsubNode
ALTER TABLE pubsubNode DROP CONSTRAINT pubsubNode_pk;
RENAME pubsubNode TO ofPubsubNode;
ALTER TABLE ofPubsubNode ADD CONSTRAINT ofPubsubNode_pk PRIMARY KEY (serviceID, nodeID);

-- Rename pubsubNodeJIDs to ofPubsubNodeJIDs
ALTER TABLE pubsubNodeJIDs DROP CONSTRAINT pubsubJID_pk;
RENAME pubsubNodeJIDs TO ofPubsubNodeJIDs;
ALTER TABLE ofPubsubNodeJIDs ADD CONSTRAINT ofPubsubNdJIDs_pk PRIMARY KEY (serviceID, nodeID, jid);

-- Rename pubsubNodeGroups to ofPubsubNodeGroups
DROP INDEX pubsubNGrps_idx;
RENAME pubsubNodeGroups TO ofPubsubNodeGroups;
CREATE INDEX ofPubsubNGrps_idx ON ofPubsubNodeGroups (serviceID, nodeID);

-- Rename pubsubAffiliation to ofPubsubAffiliation
ALTER TABLE pubsubAffiliation DROP CONSTRAINT pubsubAffil_pk;
RENAME pubsubAffiliation TO ofPubsubAffiliation;
ALTER TABLE ofPubsubAffiliation ADD CONSTRAINT ofPubsubAffil_pk PRIMARY KEY (serviceID, nodeID, jid);

-- Rename pubsubItem to ofPubsubItem
ALTER TABLE pubsubItem DROP CONSTRAINT pubsubItem_pk;
RENAME pubsubItem TO ofPubsubItem;
ALTER TABLE ofPubsubItem ADD CONSTRAINT ofPubsubItem_pk PRIMARY KEY (serviceID, nodeID, id);

-- Rename pubsubSubscription to ofPubsubSubscription
ALTER TABLE pubsubSubscription DROP CONSTRAINT pubsubSubs_pk;
RENAME pubsubSubscription TO ofPubsubSubscription;
ALTER TABLE ofPubsubSubscription ADD CONSTRAINT ofPubsubSubs_pk PRIMARY KEY (serviceID, nodeID, id);

-- Rename pubsubDefaultConf to ofPubsubDefaultConf
ALTER TABLE pubsubDefaultConf DROP CONSTRAINT pubsubDefConf_pk;
RENAME pubsubDefaultConf TO ofPubsubDefaultConf;
ALTER TABLE ofPubsubDefaultConf ADD CONSTRAINT ofPubsubDefConf_pk PRIMARY KEY (serviceID, leaf);

-- Update version
UPDATE ofVersion SET version = 19 WHERE name = 'openfire';
