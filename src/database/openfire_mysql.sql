# $Revision: 1650 $
# $Date: 2005-07-20 00:18:17 -0300 (Wed, 20 Jul 2005) $

CREATE TABLE jiveUser (
  username              VARCHAR(64)     NOT NULL,
  plainPassword         VARCHAR(32),
  encryptedPassword     VARCHAR(255),
  name                  VARCHAR(100),
  email                 VARCHAR(100),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  PRIMARY KEY (username),
  INDEX jiveUser_cDate_idx (creationDate)
);

CREATE TABLE jiveUserProp (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             TEXT            NOT NULL,
  PRIMARY KEY (username, name)
);

CREATE TABLE jiveUserFlag (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  startTime             CHAR(15),
  endTime               CHAR(15),
  PRIMARY KEY (username, name),
  INDEX jiveUser_sTime_idx (startTime),
  INDEX jiveUser_eTime_idx (endTime)
);

CREATE TABLE jiveGroup (
  groupName             VARCHAR(50)     NOT NULL,
  description           VARCHAR(255),
  PRIMARY KEY (groupName)
);

CREATE TABLE jiveGroupProp (
  groupName             VARCHAR(50)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             TEXT            NOT NULL,
  PRIMARY KEY (groupName, name)
);

CREATE TABLE jiveGroupUser (
  groupName             VARCHAR(50)     NOT NULL,
  username              VARCHAR(100)    NOT NULL,
  administrator         TINYINT         NOT NULL,
  PRIMARY KEY (groupName, username, administrator)
);

CREATE TABLE jivePrivate (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(200)    NOT NULL,
  privateData           TEXT            NOT NULL,
  PRIMARY KEY (username, name, namespace(100))
);

CREATE TABLE jiveOffline (
  username              VARCHAR(64)     NOT NULL,
  messageID             BIGINT          NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  stanza                TEXT            NOT NULL,
  PRIMARY KEY (username, messageID)
);

CREATE TABLE jivePresence (
  username              VARCHAR(64)     NOT NULL,
  offlinePresence       TEXT,
  offlineDate           CHAR(15)     NOT NULL,
  PRIMARY KEY (username)
);

CREATE TABLE jiveRoster (
  rosterID              BIGINT          NOT NULL,
  username              VARCHAR(64)     NOT NULL,
  jid                   VARCHAR(1024)   NOT NULL,
  sub                   TINYINT         NOT NULL,
  ask                   TINYINT         NOT NULL,
  recv                  TINYINT         NOT NULL,
  nick                  VARCHAR(255),
  PRIMARY KEY (rosterID),
  INDEX jiveRoster_unameid_idx (username),
  INDEX jiveRoster_jid_idx (jid)
);

CREATE TABLE jiveRosterGroups (
  rosterID              BIGINT          NOT NULL,
  rank                  TINYINT         NOT NULL,
  groupName             VARCHAR(255)    NOT NULL,
  PRIMARY KEY (rosterID, rank),
  INDEX jiveRosterGroup_rosterid_idx (rosterID)
);

CREATE TABLE jiveVCard (
  username              VARCHAR(64)     NOT NULL,
  vcard                 MEDIUMTEXT      NOT NULL,
  PRIMARY KEY (username)
);

CREATE TABLE jiveID (
  idType                INTEGER         NOT NULL,
  id                    BIGINT          NOT NULL,
  PRIMARY KEY (idType)
);

CREATE TABLE jiveProperty (
  name        VARCHAR(100)              NOT NULL,
  propValue   TEXT                      NOT NULL,
  PRIMARY KEY (name)
);


CREATE TABLE jiveVersion (
  name     VARCHAR(50)  NOT NULL,
  version  INTEGER  NOT NULL,
  PRIMARY KEY (name)
);

CREATE TABLE jiveExtComponentConf (
  subdomain             VARCHAR(255)    NOT NULL,
  secret                VARCHAR(255),
  permission            VARCHAR(10)     NOT NULL,
  PRIMARY KEY (subdomain)
);

CREATE TABLE jiveRemoteServerConf (
  xmppDomain            VARCHAR(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR(10)     NOT NULL,
  PRIMARY KEY (xmppDomain)
);

CREATE TABLE jivePrivacyList (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  isDefault             TINYINT         NOT NULL,
  list                  TEXT            NOT NULL,
  PRIMARY KEY (username, name),
  INDEX jivePList_default_idx (username, isDefault)
);

CREATE TABLE jiveSASLAuthorized (
  username            VARCHAR(64)   NOT NULL,
  principal           TEXT          NOT NULL,
  PRIMARY KEY (username, principal(200))
);

CREATE TABLE jiveSecurityAuditLog (
  msgID                 BIGINT          NOT NULL,
  username              VARCHAR(64)     NOT NULL,
  entryStamp            BIGINT          NOT NULL,
  summary               VARCHAR(255)    NOT NULL,
  node                  VARCHAR(255)    NOT NULL,
  details               TEXT,
  PRIMARY KEY (msgID),
  INDEX jiveSecAuditLog_tstamp_idx (entryStamp),
  INDEX jiveSecAuditLog_uname_idx (username)
);

# MUC Tables

CREATE TABLE mucService (
  serviceID           BIGINT        NOT NULL,
  subdomain           VARCHAR(255)  NOT NULL,
  description         VARCHAR(255),
  isHidden            TINYINT       NOT NULL,
  PRIMARY KEY (subdomain),
  INDEX mucService_serviceid_idx (serviceID)
);

CREATE TABLE mucServiceProp (
  serviceID           BIGINT        NOT NULL,
  name                VARCHAR(100)  NOT NULL,
  propValue           TEXT          NOT NULL,
  PRIMARY KEY (serviceID, name)
);

CREATE TABLE mucRoom (
  serviceID           BIGINT        NOT NULL,
  roomID              BIGINT        NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  naturalName         VARCHAR(255)  NOT NULL,
  description         VARCHAR(255),
  lockedDate          CHAR(15)      NOT NULL,
  emptyDate           CHAR(15)      NULL,
  canChangeSubject    TINYINT       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          TINYINT       NOT NULL,
  moderated           TINYINT       NOT NULL,
  membersOnly         TINYINT       NOT NULL,
  canInvite           TINYINT       NOT NULL,
  roomPassword        VARCHAR(50)   NULL,
  canDiscoverJID      TINYINT       NOT NULL,
  logEnabled          TINYINT       NOT NULL,
  subject             VARCHAR(100)  NULL,
  rolesToBroadcast    TINYINT       NOT NULL,
  useReservedNick     TINYINT       NOT NULL,
  canChangeNick       TINYINT       NOT NULL,
  canRegister         TINYINT       NOT NULL,
  PRIMARY KEY (serviceID,name),
  INDEX mucRoom_roomid_idx (roomID),
  INDEX mucRoom_serviceid_idx (serviceID)
);

CREATE TABLE mucRoomProp (
  roomID                BIGINT          NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             TEXT            NOT NULL,
  PRIMARY KEY (roomID, name)
);

CREATE TABLE mucAffiliation (
  roomID              BIGINT        NOT NULL,
  jid                 TEXT          NOT NULL,
  affiliation         TINYINT       NOT NULL,
  PRIMARY KEY (roomID,jid(70))
);

CREATE TABLE mucMember (
  roomID              BIGINT        NOT NULL,
  jid                 TEXT          NOT NULL,
  nickname            VARCHAR(255)  NULL,
  firstName           VARCHAR(100)  NULL,
  lastName            VARCHAR(100)  NULL,
  url                 VARCHAR(100)  NULL,
  email               VARCHAR(100)  NULL,
  faqentry            VARCHAR(100)  NULL,
  PRIMARY KEY (roomID,jid(70))
);

CREATE TABLE mucConversationLog (
  roomID              BIGINT        NOT NULL,
  sender              TEXT          NOT NULL,
  nickname            VARCHAR(255)  NULL,
  logTime             CHAR(15)      NOT NULL,
  subject             VARCHAR(255)  NULL,
  body                TEXT          NULL,
  INDEX mucLog_time_idx (logTime)
);

# PubSub Tables

CREATE TABLE pubsubNode (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  leaf                TINYINT       NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  parent              VARCHAR(100)  NULL,
  deliverPayloads     TINYINT       NOT NULL,
  maxPayloadSize      INTEGER       NULL,
  persistItems        TINYINT       NULL,
  maxItems            INTEGER       NULL,
  notifyConfigChanges TINYINT       NOT NULL,
  notifyDelete        TINYINT       NOT NULL,
  notifyRetract       TINYINT       NOT NULL,
  presenceBased       TINYINT       NOT NULL,
  sendItemSubscribe   TINYINT       NOT NULL,
  publisherModel      VARCHAR(15)   NOT NULL,
  subscriptionEnabled TINYINT       NOT NULL,
  configSubscription  TINYINT       NOT NULL,
  accessModel         VARCHAR(10)   NOT NULL,
  payloadType         VARCHAR(100)  NULL,
  bodyXSLT            VARCHAR(100)  NULL,
  dataformXSLT        VARCHAR(100)  NULL,
  creator             VARCHAR(255) NOT NULL,
  description         VARCHAR(255)  NULL,
  language            VARCHAR(255)  NULL,
  name                VARCHAR(50)   NULL,
  replyPolicy         VARCHAR(15)   NULL,
  associationPolicy   VARCHAR(15)   NULL,
  maxLeafNodes        INTEGER       NULL,
  PRIMARY KEY (serviceID, nodeID)
);

CREATE TABLE pubsubNodeJIDs (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(255)  NOT NULL,
  associationType     VARCHAR(20)   NOT NULL,
  PRIMARY KEY (serviceID, nodeID, jid(70))
);

CREATE TABLE pubsubNodeGroups (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  rosterGroup         VARCHAR(100)   NOT NULL,
  INDEX pubsubNodeGroups_idx (serviceID, nodeID)
);

CREATE TABLE pubsubAffiliation (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(255) NOT NULL,
  affiliation         VARCHAR(10)   NOT NULL,
  PRIMARY KEY (serviceID, nodeID, jid(70))
);

CREATE TABLE pubsubItem (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  id                  VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(255)  NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  payload             MEDIUMTEXT    NULL,
  PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubSubscription (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  id                  VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(255) NOT NULL,
  owner               VARCHAR(255) NOT NULL,
  state               VARCHAR(15)   NOT NULL,
  deliver             TINYINT       NOT NULL,
  digest              TINYINT       NOT NULL,
  digest_frequency    INT           NOT NULL,
  expire              CHAR(15)      NULL,
  includeBody         TINYINT       NOT NULL,
  showValues          VARCHAR(30)   NULL,
  subscriptionType    VARCHAR(10)   NOT NULL,
  subscriptionDepth   TINYINT       NOT NULL,
  keyword             VARCHAR(200)  NULL,
  PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubDefaultConf (
  serviceID           VARCHAR(100)  NOT NULL,
  leaf                TINYINT       NOT NULL,
  deliverPayloads     TINYINT       NOT NULL,
  maxPayloadSize      INTEGER       NOT NULL,
  persistItems        TINYINT       NOT NULL,
  maxItems            INTEGER       NOT NULL,
  notifyConfigChanges TINYINT       NOT NULL,
  notifyDelete        TINYINT       NOT NULL,
  notifyRetract       TINYINT       NOT NULL,
  presenceBased       TINYINT       NOT NULL,
  sendItemSubscribe   TINYINT       NOT NULL,
  publisherModel      VARCHAR(15)   NOT NULL,
  subscriptionEnabled TINYINT       NOT NULL,
  accessModel         VARCHAR(10)   NOT NULL,
  language            VARCHAR(255)  NULL,
  replyPolicy         VARCHAR(15)   NULL,
  associationPolicy   VARCHAR(15)   NOT NULL,
  maxLeafNodes        INTEGER       NOT NULL,
  PRIMARY KEY (serviceID, leaf)
);

# Finally, insert default table values.

INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
INSERT INTO jiveID (idType, id) VALUES (23, 1);
INSERT INTO jiveID (idType, id) VALUES (26, 1);

INSERT INTO jiveVersion (name, version) VALUES ('openfire', 18);

# Entry for admin user
INSERT INTO jiveUser (username, plainPassword, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');

# Entry for default conference service
INSERT INTO mucService (serviceID, subdomain, isHidden) VALUES (1, 'conference', 0);
