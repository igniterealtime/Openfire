# Rename jiveUser to ofUser
ALTER TABLE jiveUser DROP INDEX jiveUser_cDate_idx;
RENAME TABLE jiveUser TO ofUser;
ALTER TABLE ofUser ADD INDEX ofUser_cDate_idx (creationDate);

# Rename jiveUserProp to ofUserProp
RENAME TABLE jiveUserProp TO ofUserProp;

# Rename jiveUserFlag to ofUserFlag
ALTER TABLE jiveUserFlag DROP INDEX jiveUser_sTime_idx;
ALTER TABLE jiveUserFlag DROP INDEX jiveUser_eTime_idx;
RENAME TABLE jiveUserFlag TO ofUserFlag;
ALTER TABLE ofUserFlag ADD INDEX ofUserFlag_sTime_idx (startTime);
ALTER TABLE ofUserFlag ADD INDEX ofUserFlag_eTime_idx (endTime);

# Rename jiveGroup to ofGroup
RENAME TABLE jiveGroup TO ofGroup;

# Rename jiveGroupProp to ofGroupProp
RENAME TABLE jiveGroupProp TO ofGroupProp;

# Rename jiveGroupUser to ofGroupUser
RENAME TABLE jiveGroupUser TO ofGroupUser;

# Rename jivePrivate to ofPrivate
RENAME TABLE jivePrivate TO ofPrivate;

# Rename jiveOffline to ofOffline
RENAME TABLE jiveOffline TO ofOffline;

# Rename jivePresence to ofPresence
RENAME TABLE jivePresence TO ofPresence;

# Make sure that the jid column of jiveRoster is a varchar instead of text
ALTER TABLE jiveRoster CHANGE COLUMN jid jid VARCHAR(1024) NOT NULL;

# Rename jiveRoster to ofRoster
ALTER TABLE jiveRoster DROP INDEX jiveRoster_unameid_idx;
ALTER TABLE jiveRoster DROP INDEX jiveRoster_jid_idx;
RENAME TABLE jiveRoster TO ofRoster;
ALTER TABLE ofRoster ADD INDEX ofRoster_unameid_idx (username);
ALTER TABLE ofRoster ADD INDEX ofRoster_jid_idx (jid);

# Rename jiveRosterGroups to ofRosterGroups
ALTER TABLE jiveRosterGroups DROP INDEX jiveRosterGroup_rosterid_idx;
RENAME TABLE jiveRosterGroups TO ofRosterGroups;
ALTER TABLE ofRosterGroups ADD INDEX ofRosterGroup_rosterid_idx (rosterID);

# Rename jiveVCard to ofVCard
RENAME TABLE jiveVCard TO ofVCard;

# Rename jiveID to ofID
RENAME TABLE jiveID TO ofID;

# Rename jiveProperty to ofProperty
RENAME TABLE jiveProperty TO ofProperty;

# Rename jiveVersion to ofVersion
RENAME TABLE jiveVersion TO ofVersion;

# Rename jiveExtComponentConf to ofExtComponentConf
RENAME TABLE jiveExtComponentConf TO ofExtComponentConf;

# Rename jiveRemoteServerConf to ofRemoteServerConf
RENAME TABLE jiveRemoteServerConf TO ofRemoteServerConf;

# Rename jivePrivacyList to ofPrivacyList
ALTER TABLE jivePrivacyList DROP INDEX jivePList_default_idx;
RENAME TABLE jivePrivacyList TO ofPrivacyList;
ALTER TABLE ofPrivacyList ADD INDEX ofPrivacyList_default_idx (username, isDefault);

# Rename jiveSASLAuthorized to ofSASLAuthorized
RENAME TABLE jiveSASLAuthorized TO ofSASLAuthorized;

# Rename jiveSecurityAuditLog to ofSecurityAuditLog
ALTER TABLE jiveSecurityAuditLog DROP INDEX jiveSecAuditLog_tstamp_idx;
ALTER TABLE jiveSecurityAuditLog DROP INDEX jiveSecAuditLog_uname_idx;
RENAME TABLE jiveSecurityAuditLog TO ofSecurityAuditLog;
ALTER TABLE ofSecurityAuditLog ADD INDEX ofSecurityAuditLog_tstamp_idx (entryStamp);
ALTER TABLE ofSecurityAuditLog ADD INDEX ofSecurityAuditLog_uname_idx (username);

# Rename mucService to ofMucService
ALTER TABLE mucService DROP INDEX mucService_serviceid_idx;
RENAME TABLE mucService TO ofMucService;
ALTER TABLE ofMucService ADD INDEX ofMucService_serviceid_idx (serviceID);

# Rename mucServiceProp to ofMucServiceProp
RENAME TABLE mucServiceProp TO ofMucServiceProp;

# Rename mucRoom to ofMucRoom
ALTER TABLE mucRoom DROP INDEX mucRoom_roomid_idx;
ALTER TABLE mucRoom DROP INDEX mucRoom_serviceid_idx;
RENAME TABLE mucRoom TO ofMucRoom;
ALTER TABLE ofMucRoom ADD INDEX ofMucRoom_roomid_idx (roomID);
ALTER TABLE ofMucRoom ADD INDEX ofMucRoom_serviceid_idx (serviceID);

# Rename mucRoomProp to ofMucRoomProp
RENAME TABLE mucRoomProp TO ofMucRoomProp;

# Rename mucAffiliation to ofMucAffiliation
RENAME TABLE mucAffiliation TO ofMucAffiliation;

# Rename mucMember to ofMucMember
RENAME TABLE mucMember TO ofMucMember;

# Rename mucConversationLog to ofMucConversationLog
ALTER TABLE mucConversationLog DROP INDEX mucLog_time_idx;
RENAME TABLE mucConversationLog TO ofMucConversationLog;
ALTER TABLE ofMucConversationLog ADD INDEX ofMucConversationLog_time_idx (logTime);

# Rename pubsubNode to ofPubsubNode
RENAME TABLE pubsubNode TO ofPubsubNode;

# Rename pubsubNodeJIDs to ofPubsubNodeJIDs
RENAME TABLE pubsubNodeJIDs TO ofPubsubNodeJIDs;

# Rename pubsubNodeGroups to ofPubsubNodeGroups
ALTER TABLE pubsubNodeGroups DROP INDEX pubsubNodeGroups_idx;
RENAME TABLE pubsubNodeGroups TO ofPubsubNodeGroups;
ALTER TABLE ofPubsubNodeGroups ADD INDEX ofPubsubNodeGroups_idx (serviceID, nodeID);

# Rename pubsubAffiliation to ofPubsubAffiliation
RENAME TABLE pubsubAffiliation TO ofPubsubAffiliation;

# Rename pubsubItem to ofPubsubItem
RENAME TABLE pubsubItem TO ofPubsubItem;

# Rename pubsubSubscription to ofPubsubSubscription
RENAME TABLE pubsubSubscription TO ofPubsubSubscription;

# Rename pubsubDefaultConf to ofPubsubDefaultConf
RENAME TABLE pubsubDefaultConf TO ofPubsubDefaultConf;

# Update version
UPDATE ofVersion SET version = 19 WHERE name = 'openfire';
