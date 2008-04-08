// $Revision: 1650 $
// $Date: 2005-07-20 00:18:17 -0300 (Wed, 20 Jul 2005) $

CREATE TABLE jiveUser (
  username              VARCHAR(64)     NOT NULL,
  plainPassword         VARCHAR(32),
  encryptedPassword     VARCHAR(255),
  name                  VARCHAR(100),
  email                 VARCHAR(100),
  creationDate          VARCHAR(15)     NOT NULL,
  modificationDate      VARCHAR(15)     NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (username)
);
CREATE INDEX jiveUser_cDate_idx ON jiveUser (creationDate);


CREATE TABLE jiveUserProp (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(4000)   NOT NULL,
  CONSTRAINT jiveUserProp_pk PRIMARY KEY (username, name)
);


CREATE TABLE jiveUserFlag (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  startTime             VARCHAR(15),
  endTime               VARCHAR(15),
  CONSTRAINT jiveUserFlag_pk PRIMARY KEY (username, name)
);
CREATE INDEX jiveUserFlag_sTime_idx ON jiveUserFlag (startTime);
CREATE INDEX jiveUserFlag_eTime_idx ON jiveUserFlag (endTime);


CREATE TABLE jivePrivate (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(200)    NOT NULL,
  privateData           LONGVARCHAR     NOT NULL,
  CONSTRAINT jivePrivate_pk PRIMARY KEY (username, name, namespace)
);


CREATE TABLE jiveOffline (
  username              VARCHAR(64)     NOT NULL,
  messageID             BIGINT          NOT NULL,
  creationDate          VARCHAR(15)     NOT NULL,
  messageSize           INTEGER         NOT NULL,
  stanza                LONGVARCHAR     NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID)
);

CREATE TABLE jivePresence (
  username              VARCHAR(64)     NOT NULL,
  offlinePresence       LONGVARCHAR,
  offlineDate           VARCHAR(15)     NOT NULL,
  CONSTRAINT jivePresence_pk PRIMARY KEY (username)
);

CREATE TABLE jiveRoster (
  rosterID              BIGINT          NOT NULL,
  username              VARCHAR(64)     NOT NULL,
  jid                   VARCHAR(1024)   NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  VARCHAR(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveRoster_username_idx ON jiveRoster (username);
CREATE INDEX jiveRoster_jid_idx ON jiveRoster (jid);


CREATE TABLE jiveRosterGroups (
  rosterID              BIGINT          NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             VARCHAR(255)    NOT NULL,
  CONSTRAINT jiveRosterGroups_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRosterGroup_rosterid_idx ON jiveRosterGroups (rosterID);


CREATE TABLE jiveVCard (
  username              VARCHAR(64)     NOT NULL,
  vcard                 LONGVARCHAR     NOT NULL,
  CONSTRAINT jiveVCard_pk PRIMARY KEY (username)
);


CREATE TABLE jiveGroup (
  groupName              VARCHAR(50)     NOT NULL,
  description           VARCHAR(255),
  CONSTRAINT jiveGroup_pk PRIMARY KEY (groupName)
);


CREATE TABLE jiveGroupProp (
  groupName             VARCHAR(50)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(4000)   NOT NULL,
  CONSTRAINT jiveGroupProp_pk PRIMARY KEY (groupName, name)
);


CREATE TABLE jiveGroupUser (
  groupName             VARCHAR(50)     NOT NULL,
  username              VARCHAR(100)    NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser_pk PRIMARY KEY (groupName, username, administrator)
);


CREATE TABLE jiveID (
  idType                INTEGER         NOT NULL,
  id                    BIGINT          NOT NULL,
  CONSTRAINT jiveID_pk PRIMARY KEY (idType)
);


CREATE TABLE jiveProperty (
  name        VARCHAR(100)  NOT NULL,
  propValue   VARCHAR(4000) NOT NULL,
  CONSTRAINT jiveProperty_pk PRIMARY KEY (name)
);


CREATE TABLE jiveVersion (
  name  varchar(50)  NOT NULL,
  version  INTEGER  NOT NULL,
  CONSTRAINT jiveVersion_pk PRIMARY KEY (name)
);

CREATE TABLE jiveExtComponentConf (
  subdomain             VARCHAR(255)    NOT NULL,
  secret                VARCHAR(255),
  permission            VARCHAR(10)     NOT NULL,
  CONSTRAINT jiveExtComponentConf_pk PRIMARY KEY (subdomain)
);

CREATE TABLE jiveRemoteServerConf (
  xmppDomain            VARCHAR(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR(10)     NOT NULL,
  CONSTRAINT jiveRemoteServerConf_pk PRIMARY KEY (xmppDomain)
);

CREATE TABLE jivePrivacyList (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  isDefault             INTEGER         NOT NULL,
  list                  LONGVARCHAR     NOT NULL,
  CONSTRAINT jivePrivacyList_pk PRIMARY KEY (username, name)
);
CREATE INDEX jivePList_default_idx ON jivePrivacyList (username, isDefault);

CREATE TABLE jiveSASLAuthorized (
  username        VARCHAR(64)      NOT NULL,
  principal       VARCHAR(4000)    NOT NULL,
  CONSTRAINT jiveSASLAuthorized_pk PRIMARY KEY (username, principal)
);

CREATE TABLE jiveSecurityAuditLog (
  msgID                 BIGINT          NOT NULL,
  username              VARCHAR(64)     NOT NULL,
  entryStamp            BIGINT          NOT NULL,
  summary               VARCHAR(255)    NOT NULL,
  node                  VARCHAR(255)    NOT NULL,
  details               LONGVARCHAR,
  CONSTRAINT jiveSecAuditLog_pk PRIMARY KEY (msgID)
);
CREATE INDEX jiveSecAuditLog_tstamp_idx ON jiveSecurityAuditLog (entryStamp);
CREATE INDEX jiveSecAuditLog_uname_idx ON jiveSecurityAuditLog (username);

// MUC Tables

CREATE TABLE mucRoom (
  roomID              BIGINT        NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  naturalName         VARCHAR(255)  NOT NULL,
  description         VARCHAR(255),
  lockedDate          CHAR(15)      NOT NULL,
  emptyDate           CHAR(15)      NULL,
  canChangeSubject    INTEGER       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          INTEGER       NOT NULL,
  moderated           INTEGER       NOT NULL,
  membersOnly         INTEGER       NOT NULL,
  canInvite           INTEGER       NOT NULL,
  roomPassword        VARCHAR(50)   NULL,
  canDiscoverJID      INTEGER       NOT NULL,
  logEnabled          INTEGER       NOT NULL,
  subject             VARCHAR(100)  NULL,
  rolesToBroadcast    INTEGER       NOT NULL,
  useReservedNick     INTEGER       NOT NULL,
  canChangeNick       INTEGER       NOT NULL,
  canRegister         INTEGER       NOT NULL,
  CONSTRAINT mucRoom_pk PRIMARY KEY (name)
);

CREATE INDEX mucRoom_roomid_idx ON mucRoom(roomID);

CREATE TABLE mucRoomProp (
  roomID                BIGINT          NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(4000)   NOT NULL,
  CONSTRAINT mucRoomProp_pk PRIMARY KEY (roomID, name)
);

CREATE TABLE mucAffiliation (
  roomID              BIGINT        NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  affiliation         INTEGER       NOT NULL,
  CONSTRAINT mucAffiliation_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucMember (
  roomID              BIGINT        NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  nickname            VARCHAR(255)  NULL,
  firstName           VARCHAR(100)  NULL,
  lastName            VARCHAR(100)  NULL,
  url                 VARCHAR(100)  NULL,
  email               VARCHAR(100)  NULL,
  faqentry            VARCHAR(100)  NULL,
  CONSTRAINT mucMember_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucConversationLog (
  roomID              BIGINT        NOT NULL,
  sender              VARCHAR(1024) NOT NULL,
  nickname            VARCHAR(255)  NULL,
  logTime             CHAR(15)      NOT NULL,
  subject             VARCHAR(255)  NULL,
  body                LONGVARCHAR   NULL
);
CREATE INDEX mucLog_time_idx ON mucConversationLog (logTime);

// PubSub Tables

CREATE TABLE pubsubNode (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  leaf                INTEGER       NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  parent              VARCHAR(100)  NULL,
  deliverPayloads     INTEGER       NOT NULL,
  maxPayloadSize      INTEGER       NULL,
  persistItems        INTEGER       NULL,
  maxItems            INTEGER       NULL,
  notifyConfigChanges INTEGER       NOT NULL,
  notifyDelete        INTEGER       NOT NULL,
  notifyRetract       INTEGER       NOT NULL,
  presenceBased       INTEGER       NOT NULL,
  sendItemSubscribe   INTEGER       NOT NULL,
  publisherModel      VARCHAR(15)   NOT NULL,
  subscriptionEnabled INTEGER       NOT NULL,
  configSubscription  INTEGER       NOT NULL,
  accessModel         VARCHAR(10)   NOT NULL,
  payloadType         VARCHAR(100)  NULL,
  bodyXSLT            VARCHAR(100)  NULL,
  dataformXSLT        VARCHAR(100)  NULL,
  creator             VARCHAR(1024) NOT NULL,
  description         VARCHAR(255)  NULL,
  language            VARCHAR(255)  NULL,
  name                VARCHAR(50)   NULL,
  replyPolicy         VARCHAR(15)   NULL,
  associationPolicy   VARCHAR(15)   NULL,
  maxLeafNodes        INTEGER       NULL,
  CONSTRAINT pubsubNode_pk PRIMARY KEY (serviceID, nodeID)
);

CREATE TABLE pubsubNodeJIDs (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  associationType     VARCHAR(20)   NOT NULL,
  CONSTRAINT pubsubJID_pk PRIMARY KEY (serviceID, nodeID, jid)
);

CREATE TABLE pubsubNodeGroups (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  rosterGroup         VARCHAR(100)  NOT NULL
);
CREATE INDEX pubsubNodeGroups_idx ON pubsubNodeGroups (serviceID, nodeID);

CREATE TABLE pubsubAffiliation (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  affiliation         VARCHAR(10)   NOT NULL,
  CONSTRAINT pubsubAffil_pk PRIMARY KEY (serviceID, nodeID, jid)
);

CREATE TABLE pubsubItem (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  id                  VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  payload             VARCHAR(4000) NULL,
  CONSTRAINT pubsubItem_pk PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubSubscription (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  id                  VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  owner               VARCHAR(1024) NOT NULL,
  state               VARCHAR(15)   NOT NULL,
  deliver             INTEGER       NOT NULL,
  digest              INTEGER       NOT NULL,
  digest_frequency    INTEGER       NOT NULL,
  expire              CHAR(15)      NULL,
  includeBody         INTEGER       NOT NULL,
  showValues          VARCHAR(30)   NOT NULL,
  subscriptionType    VARCHAR(10)   NOT NULL,
  subscriptionDepth   INTEGER       NOT NULL,
  keyword             VARCHAR(200)  NULL,
  CONSTRAINT pubsubSubs_pk PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubDefaultConf (
  serviceID           VARCHAR(100)  NOT NULL,
  leaf                INTEGER       NOT NULL,
  deliverPayloads     INTEGER       NOT NULL,
  maxPayloadSize      INTEGER       NOT NULL,
  persistItems        INTEGER       NOT NULL,
  maxItems            INTEGER       NOT NULL,
  notifyConfigChanges INTEGER       NOT NULL,
  notifyDelete        INTEGER       NOT NULL,
  notifyRetract       INTEGER       NOT NULL,
  presenceBased       INTEGER       NOT NULL,
  sendItemSubscribe   INTEGER       NOT NULL,
  publisherModel      VARCHAR(15)   NOT NULL,
  subscriptionEnabled INTEGER       NOT NULL,
  accessModel         VARCHAR(10)   NOT NULL,
  language            VARCHAR(255)  NULL,
  replyPolicy         VARCHAR(15)   NULL,
  associationPolicy   VARCHAR(15)   NOT NULL,
  maxLeafNodes        INTEGER       NOT NULL,
  CONSTRAINT pubsubDefConf_pk PRIMARY KEY (serviceID, leaf)
);

// Finally, insert default table values.

INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
INSERT INTO jiveID (idType, id) VALUES (23, 1);

INSERT INTO jiveVersion (name, version) VALUES ('openfire', 16);

// Entry for admin user
INSERT INTO jiveUser (username, plainPassword, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');