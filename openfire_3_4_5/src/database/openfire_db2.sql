--   $Revision: 1650 $
--   $Date: 2005-07-20 00:18:17 -0300 (Wed, 20 Jul 2005) $

CREATE TABLE jiveUser (
  username              VARCHAR(64)     NOT NULL,
  plainPassword         VARCHAR(32),
  encryptedPassword     VARCHAR(255),
  name                  VARCHAR(100),
  email                 VARCHAR(100),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (username)
);
CREATE INDEX jiveUsr_cDate_idx ON jiveUser (creationDate ASC);


CREATE TABLE jiveUserProp (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(2000)   NOT NULL,
  CONSTRAINT jiveUsrProp_pk PRIMARY KEY (username, name)
);


CREATE TABLE jivePrivate (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(200)    NOT NULL,
  privateData           VARCHAR(2000)   NOT NULL,
  CONSTRAINT jivePrivate_pk PRIMARY KEY (username, name, namespace)
);


CREATE TABLE jiveOffline (
  username              VARCHAR(64)     NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  stanza                VARCHAR(2000)   NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID)
);

CREATE TABLE jivePresence (
  username              VARCHAR(64)     NOT NULL,
  offlinePresence       VARCHAR(2000),
  offlineDate           CHAR(15)     NOT NULL,
  CONSTRAINT jivePresence_pk PRIMARY KEY (username)
);

CREATE TABLE jiveRoster (
  rosterID              INTEGER         NOT NULL,
  username              VARCHAR(64)     NOT NULL,
  jid                   VARCHAR(2000)   NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  VARCHAR(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveR_userid_idx ON jiveRoster (username ASC);


CREATE TABLE jiveRosterGroups (
  rosterID              INTEGER         NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             VARCHAR(255)    NOT NULL,
  CONSTRAINT jiveRoGrps_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRoGrps_rid_idx ON jiveRosterGroups (rosterID ASC);


CREATE TABLE jiveVCard (
  username              VARCHAR(64)     NOT NULL,
  vcard                 VARCHAR(2000)   NOT NULL,
  CONSTRAINT jiveVCard_pk PRIMARY KEY (username)
);


CREATE TABLE jiveGroup (
  groupName             VARCHAR(50)     NOT NULL,
  description           VARCHAR(255),
  CONSTRAINT jiveGroup_pk PRIMARY KEY (groupName)
);


CREATE TABLE jiveGroupProp (
  groupName             VARCHAR(50)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(2000)   NOT NULL,
  CONSTRAINT jiveGrpProp_pk PRIMARY KEY (groupName, name)
);


CREATE TABLE jiveGroupUser (
  groupName             VARCHAR(50)     NOT NULL,
  username              VARCHAR(100)    NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGrpUser PRIMARY KEY (groupName, username, administrator)
);


CREATE TABLE jiveID (
  idType                INTEGER         NOT NULL,
  id                    INTEGER         NOT NULL,
  CONSTRAINT jiveID_pk PRIMARY KEY (idType)
);


CREATE TABLE jiveProperty (
  name        VARCHAR(100) NOT NULL,
  propValue   VARCHAR(3000) NOT NULL,
  CONSTRAINT jiveProperty_pk PRIMARY KEY (name)
);


CREATE TABLE jiveVersion (
  name     VARCHAR(50)  NOT NULL,
  version  INTEGER  NOT NULL,
  CONSTRAINT jiveVersion_pk PRIMARY KEY (name)
);


CREATE TABLE jiveExtComponentConf (
  subdomain             VARCHAR(255)    NOT NULL,
  secret                VARCHAR(255),
  permission            VARCHAR(10)     NOT NULL,
  CONSTRAINT jiveExtCmpConf_pk PRIMARY KEY (subdomain)
);


CREATE TABLE jiveRemoteServerConf (
  xmppDomain            VARCHAR(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR(10)     NOT NULL,
  CONSTRAINT jiveRmSrvConf_pk PRIMARY KEY (xmppDomain)
);


CREATE TABLE jivePrivacyList (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  isDefault             INTEGER         NOT NULL,
  list                  VARCHAR(2000)   NOT NULL,
  CONSTRAINT jivePrivacyList_pk PRIMARY KEY (username, name)
);
CREATE INDEX jList_default_idx ON jivePrivacyList (username, isDefault);


CREATE TABLE jiveSASLAuthorized (
  username            VARCHAR(64)   NOT NULL,
  principal           VARCHAR(190)  NOT NULL,
  CONSTRAINT jSASLAuthrizd_pk PRIMARY KEY (username, principal)
);

-- MUC tables

CREATE TABLE mucRoom (
  roomID              INTEGER       NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  naturalName         VARCHAR(255)  NOT NULL,
  description         VARCHAR(255),
  lockedDate          CHAR(15)      NOT NULL,
  emptyDate           CHAR(15),
  canChangeSubject    INTEGER       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          INTEGER       NOT NULL,
  moderated           INTEGER       NOT NULL,
  membersOnly         INTEGER       NOT NULL,
  canInvite           INTEGER       NOT NULL,
  roomPassword        VARCHAR(50),
  canDiscoverJID      INTEGER       NOT NULL,
  logEnabled          INTEGER       NOT NULL,
  subject             VARCHAR(100),
  rolesToBroadcast    INTEGER       NOT NULL,
  useReservedNick     INTEGER       NOT NULL,
  canChangeNick       INTEGER       NOT NULL,
  canRegister         INTEGER       NOT NULL,
  CONSTRAINT mucRoom_pk PRIMARY KEY (name)
);
CREATE INDEX mucRm_roomid_idx ON mucRoom (roomID);


CREATE TABLE mucRoomProp (
  roomID                INTEGER         NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(2000)   NOT NULL,
  CONSTRAINT mucRoomProp_pk PRIMARY KEY (roomID, name)
);


CREATE TABLE mucAffiliation (
  roomID              INTEGER       NOT NULL,
  jid                 VARCHAR(1000) NOT NULL,
  affiliation         INTEGER       NOT NULL,
  CONSTRAINT mucAffiliation_pk PRIMARY KEY (roomID, jid)
);


CREATE TABLE mucMember (
  roomID              INTEGER       NOT NULL,
  jid                 VARCHAR(1000) NOT NULL,
  nickname            VARCHAR(255),
  firstName           VARCHAR(100),
  lastName            VARCHAR(100),
  url                 VARCHAR(100),
  email               VARCHAR(100),
  faqentry            VARCHAR(100),
  CONSTRAINT mucMember_pk PRIMARY KEY (roomID, jid)
);


CREATE TABLE mucConversationLog (
  roomID              INTEGER       NOT NULL,
  sender              VARCHAR(2000) NOT NULL,
  nickname            VARCHAR(255),
  logTime             CHAR(15)      NOT NULL,
  subject             VARCHAR(255),
  body                CLOB
);
CREATE INDEX mucLog_time_idx ON mucConversationLog (logTime);


-- PubSub Tables

CREATE TABLE pubsubNode (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  leaf                INTEGER       NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  parent              VARCHAR(100),
  deliverPayloads     INTEGER       NOT NULL,
  maxPayloadSize      INTEGER,
  persistItems        INTEGER,
  maxItems            INTEGER,
  notifyConfigChanges INTEGER       NOT NULL,
  notifyDelete        INTEGER       NOT NULL,
  notifyRetract       INTEGER       NOT NULL,
  presenceBased       INTEGER       NOT NULL,
  sendItemSubscribe   INTEGER       NOT NULL,
  publisherModel      VARCHAR(15)   NOT NULL,
  subscriptionEnabled INTEGER       NOT NULL,
  configSubscription  INTEGER       NOT NULL,
  accessModel         VARCHAR(10)   NOT NULL,
  payloadType         VARCHAR(100),
  bodyXSLT            VARCHAR(100),
  dataformXSLT        VARCHAR(100),
  creator             VARCHAR(1024) NOT NULL,
  description         VARCHAR(255),
  language            VARCHAR(255),
  name                VARCHAR(50),
  replyPolicy         VARCHAR(15),
  associationPolicy   VARCHAR(15),
  maxLeafNodes        INTEGER,
  CONSTRAINT pubsubNode_pk PRIMARY KEY (serviceID, nodeID)
);


CREATE TABLE pubsubNodeJIDs (
  serviceID           VARCHAR(80)  NOT NULL,
  nodeID              VARCHAR(80)  NOT NULL,
  jid                 VARCHAR(90)  NOT NULL,
  associationType     VARCHAR(20)  NOT NULL,
  CONSTRAINT pubsubJID_pk PRIMARY KEY (serviceID, nodeID, jid)
);


CREATE TABLE pubsubNodeGroups (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  rosterGroup         VARCHAR(100)  NOT NULL
);
CREATE INDEX pubsubNGrps_idx ON pubsubNodeGroups (serviceID, nodeID);


CREATE TABLE pubsubAffiliation (
  serviceID           VARCHAR(80)  NOT NULL,
  nodeID              VARCHAR(80)  NOT NULL,
  jid                 VARCHAR(90)  NOT NULL,
  affiliation         VARCHAR(10)  NOT NULL,
  CONSTRAINT pubsubAffil_pk PRIMARY KEY (serviceID, nodeID, jid)
);


CREATE TABLE pubsubItem (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  id                  VARCHAR(20)   NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  payload             CLOB,
  CONSTRAINT pubsubItem_pk PRIMARY KEY (serviceID, nodeID, id)
);


CREATE TABLE pubsubSubscription (
  serviceID           VARCHAR(80)   NOT NULL,
  nodeID              VARCHAR(80)   NOT NULL,
  id                  VARCHAR(90)   NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  owner               VARCHAR(1024) NOT NULL,
  state               VARCHAR(15)   NOT NULL,
  deliver             INTEGER       NOT NULL,
  digest              INTEGER       NOT NULL,
  digest_frequency    INTEGER       NOT NULL,
  expire              CHAR(15),
  includeBody         INTEGER       NOT NULL,
  showValues          VARCHAR(30)   NOT NULL,
  subscriptionType    VARCHAR(10)   NOT NULL,
  subscriptionDepth   INTEGER       NOT NULL,
  keyword             VARCHAR(200),
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
  language            VARCHAR(255),
  replyPolicy         VARCHAR(15),
  associationPolicy   VARCHAR(15)   NOT NULL,
  maxLeafNodes        INTEGER       NOT NULL,
  CONSTRAINT pubsubDefConf_pk PRIMARY KEY (serviceID, leaf)
);

-- Finally, insert default table values
INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
INSERT INTO jiveID (idType, id) VALUES (23, 1);

INSERT INTO jiveVersion (name, version) VALUES ('openfire', 13);

-- Entry for admin user
INSERT INTO jiveUser (username, plainPassword, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');