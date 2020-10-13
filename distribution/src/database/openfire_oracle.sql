
CREATE TABLE ofUser (
  username              VARCHAR2(64)     NOT NULL,
  storedKey             VARCHAR(32),
  serverKey             VARCHAR(32),
  salt                  VARCHAR(32),
  iterations            INTEGER,
  plainPassword         VARCHAR2(32),
  encryptedPassword     VARCHAR2(255),
  name                  VARCHAR2(100),
  email                 VARCHAR2(100),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT ofUser_pk PRIMARY KEY (username)
);
CREATE INDEX ofUser_cDate_idx ON ofUser (creationDate ASC);


CREATE TABLE ofUserProp (
  username              VARCHAR2(64)    NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(1024)  NOT NULL,
  CONSTRAINT ofUserProp_pk PRIMARY KEY (username, name)
);


CREATE TABLE ofUserFlag (
  username              VARCHAR2(64)    NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  startTime             CHAR(15),
  endTime               CHAR(15),
  CONSTRAINT ofUserFlag_pk PRIMARY KEY (username, name)
);
CREATE INDEX ofUserFlag_sTime_idx ON ofUserFlag (startTime ASC);
CREATE INDEX ofUserFlag_eTime_idx ON ofUserFlag (endTime ASC);


CREATE TABLE ofOffline (
  username              VARCHAR2(64)    NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  stanza                LONG            NOT NULL,
  CONSTRAINT ofOffline_pk PRIMARY KEY (username, messageID)
);


CREATE TABLE ofPresence (
  username              VARCHAR2(64)    NOT NULL,
  offlinePresence       LONG,
  offlineDate           CHAR(15)        NOT NULL,
  CONSTRAINT ofPresence_pk PRIMARY KEY (username)
);


CREATE TABLE ofRoster (
  rosterID              INTEGER         NOT NULL,
  username              VARCHAR2(64)    NOT NULL,
  jid                   VARCHAR2(1024)  NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  VARCHAR2(255),
  CONSTRAINT ofRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX ofRoster_username_idx ON ofRoster (username ASC);
CREATE INDEX ofRoster_jid_idx ON ofRoster (jid ASC);


CREATE TABLE ofRosterGroups (
  rosterID              INTEGER         NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             VARCHAR2(255)   NOT NULL,
  CONSTRAINT ofRosterGroups_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX ofRosterGroup_rosterid_idx ON ofRosterGroups (rosterID ASC);
ALTER TABLE ofRosterGroups ADD CONSTRAINT ofRosterGroups_rosterID_fk FOREIGN KEY (rosterID) REFERENCES ofRoster INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE ofVCard (
  username              VARCHAR2(64)    NOT NULL,
  vcard                 LONG            NOT NULL,
  CONSTRAINT ofVCard_pk PRIMARY KEY (username)
);

CREATE TABLE ofGroup (
  groupName             VARCHAR2(50)    NOT NULL,
  description           VARCHAR2(255),
  CONSTRAINT ofGroup_pk PRIMARY KEY (groupName)
);

CREATE TABLE ofGroupProp (
  groupName             VARCHAR(50)     NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(4000)  NULL,
  CONSTRAINT ofGroupProp_pk PRIMARY KEY (groupName, name)
);

CREATE TABLE ofGroupUser (
  groupName             VARCHAR(50)     NOT NULL,
  username              VARCHAR2(100)   NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT ofGroupUser_pk PRIMARY KEY (groupName, username, administrator)
);

CREATE TABLE ofID (
  idType                INTEGER         NOT NULL,
  id                    INTEGER         NOT NULL,
  CONSTRAINT ofID_pk PRIMARY KEY (idType)
);

CREATE TABLE ofProperty (
  name        VARCHAR2(100) NOT NULL,
  propValue   VARCHAR2(4000) NOT NULL,
  encrypted   INTEGER,
  iv          CHAR(24),
  CONSTRAINT ofProperty_pk PRIMARY KEY (name)
);

CREATE TABLE ofVersion (
  name     VARCHAR2(50)  NOT NULL,
  version  INTEGER  NOT NULL,
  CONSTRAINT ofVersion_pk PRIMARY KEY (name)
);

CREATE TABLE ofExtComponentConf (
  subdomain             VARCHAR2(255)    NOT NULL,
  wildcard              INTEGER          NOT NULL,
  secret                VARCHAR2(255),
  permission            VARCHAR2(10)     NOT NULL,
  CONSTRAINT ofExtComponentConf_pk PRIMARY KEY (subdomain)
);

CREATE TABLE ofRemoteServerConf (
  xmppDomain            VARCHAR2(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR2(10)     NOT NULL,
  CONSTRAINT ofRemoteServerConf_pk PRIMARY KEY (xmppDomain)
);

CREATE TABLE ofPrivacyList (
  username              VARCHAR2(64)    NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  isDefault             INTEGER         NOT NULL,
  list                  LONG            NOT NULL,
  CONSTRAINT ofPrivacyList_pk PRIMARY KEY (username, name)
);
CREATE INDEX ofPrivacyList_default_idx ON ofPrivacyList (username, isDefault);

CREATE TABLE ofSASLAuthorized (
  username            VARCHAR(64)   NOT NULL,
  principal           VARCHAR(4000) NOT NULL,
  CONSTRAINT ofSASLAuthorized_pk PRIMARY KEY (username, principal)
);

CREATE TABLE ofSecurityAuditLog (
  msgID                 INTEGER         NOT NULL,
  username              VARCHAR2(64)    NOT NULL,
  entryStamp            INTEGER         NOT NULL,
  summary               VARCHAR2(255)   NOT NULL,
  node                  VARCHAR2(255)   NOT NULL,
  details               VARCHAR2(4000),
  CONSTRAINT ofSecurityAuditLog_pk PRIMARY KEY (msgID)
);
CREATE INDEX ofSecurityAuditLog_tstamp_idx ON ofSecurityAuditLog (entryStamp);
CREATE INDEX ofSecurityAuditLog_uname_idx ON ofSecurityAuditLog (username);

-- MUC Tables

CREATE TABLE ofMucService (
  serviceID           INT           NOT NULL,
  subdomain           VARCHAR2(255) NOT NULL,
  description         VARCHAR2(255),
  isHidden            INTEGER       NOT NULL,
  CONSTRAINT ofMucService_pk PRIMARY KEY (subdomain)
);
CREATE INDEX ofMucService_serviceid_idx ON ofMucService(serviceID);

CREATE TABLE ofMucServiceProp (
  serviceID           INT           NOT NULL,
  name                VARCHAR2(100) NOT NULL,
  propValue           VARCHAR2(1024) NOT NULL,
  CONSTRAINT ofMucServiceProp_pk PRIMARY KEY (serviceID, name)
);

CREATE TABLE ofMucRoom(
  serviceID           INT           NOT NULL,
  roomID              INT           NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR2(50)  NOT NULL,
  naturalName         VARCHAR2(255) NOT NULL,
  description         VARCHAR2(255),
  lockedDate          CHAR(15)      NOT NULL,
  emptyDate           CHAR(15)      NULL,
  canChangeSubject    INTEGER       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          INTEGER       NOT NULL,
  moderated           INTEGER       NOT NULL,
  membersOnly         INTEGER       NOT NULL,
  canInvite           INTEGER       NOT NULL,
  roomPassword        VARCHAR2(50)  NULL,
  canDiscoverJID      INTEGER       NOT NULL,
  logEnabled          INTEGER       NOT NULL,
  subject             VARCHAR2(100) NULL,
  rolesToBroadcast    INTEGER       NOT NULL,
  useReservedNick     INTEGER       NOT NULL,
  canChangeNick       INTEGER       NOT NULL,
  canRegister         INTEGER       NOT NULL,
  allowpm             INTEGER       NULL,
  fmucEnabled         INTEGER       NULL,
  fmucOutboundNode    VARCHAR2(255) NULL,
  fmucOutboundMode    INTEGER       NULL,
  fmucInboundNodes    VARCHAR2(4000) NULL,
  CONSTRAINT ofMucRoom_pk PRIMARY KEY (serviceID, name)
);
CREATE INDEX ofMucRoom_roomid_idx ON ofMucRoom (roomID);
CREATE INDEX ofMucRoom_serviceid_idx ON ofMucRoom (serviceID);

CREATE TABLE ofMucRoomProp (
  roomID                INT             NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(1024)  NOT NULL,
  CONSTRAINT ofMucRoomProp_pk PRIMARY KEY (roomID, name)
);

CREATE TABLE ofMucAffiliation (
  roomID              INT            NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  affiliation         INTEGER        NOT NULL,
  CONSTRAINT ofMucAffiliation_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE ofMucMember (
  roomID              INT            NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  nickname            VARCHAR2(255)  NULL,
  firstName           VARCHAR2(100)  NULL,
  lastName            VARCHAR2(100)  NULL,
  url                 VARCHAR2(100)  NULL,
  email               VARCHAR2(100)  NULL,
  faqentry            VARCHAR2(100)  NULL,
  CONSTRAINT ofMucMember_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE ofMucConversationLog (
  roomID              INT            NOT NULL,
  messageID              INT            NOT NULL,
  sender              VARCHAR2(1024) NOT NULL,
  nickname            VARCHAR2(255)  NULL,
  logTime             CHAR(15)       NOT NULL,
  subject             VARCHAR2(255)  NULL,
  body                VARCHAR2(4000) NULL,
  stanza                VARCHAR2(4000) NULL
);
CREATE INDEX ofMucConversationLog_time_idx ON ofMucConversationLog (logTime);
CREATE INDEX ofMucConversationLog_msg_id ON ofMucConversationLog (messageID);


-- PubSub Tables

CREATE TABLE ofPubsubNode (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  leaf                INTEGER        NOT NULL,
  creationDate        CHAR(15)       NOT NULL,
  modificationDate    CHAR(15)       NOT NULL,
  parent              VARCHAR2(100)  NULL,
  deliverPayloads     INTEGER        NOT NULL,
  maxPayloadSize      INTEGER        NULL,
  persistItems        INTEGER        NULL,
  maxItems            INTEGER        NULL,
  notifyConfigChanges INTEGER        NOT NULL,
  notifyDelete        INTEGER        NOT NULL,
  notifyRetract       INTEGER        NOT NULL,
  presenceBased       INTEGER        NOT NULL,
  sendItemSubscribe   INTEGER        NOT NULL,
  publisherModel      VARCHAR2(15)   NOT NULL,
  subscriptionEnabled INTEGER        NOT NULL,
  configSubscription  INTEGER        NOT NULL,
  accessModel         VARCHAR2(10)   NOT NULL,
  payloadType         VARCHAR2(100)  NULL,
  bodyXSLT            VARCHAR2(100)  NULL,
  dataformXSLT        VARCHAR2(100)  NULL,
  creator             VARCHAR2(1024) NOT NULL,
  description         VARCHAR2(255)  NULL,
  language            VARCHAR2(255)  NULL,
  name                VARCHAR2(50)   NULL,
  replyPolicy         VARCHAR2(15)   NULL,
  associationPolicy   VARCHAR2(15)   NULL,
  maxLeafNodes        INTEGER        NULL,
  CONSTRAINT ofPubsubNode_pk PRIMARY KEY (serviceID, nodeID)
);

CREATE TABLE ofPubsubNodeJIDs (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  associationType     VARCHAR2(20)   NOT NULL,
  CONSTRAINT ofPubsubNodeJIDs_pk PRIMARY KEY (serviceID, nodeID, jid)
);

CREATE TABLE ofPubsubNodeGroups (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  rosterGroup         VARCHAR2(100)  NOT NULL
);
CREATE INDEX ofPubsubNodeGroups_idx ON ofPubsubNodeGroups (serviceID, nodeID);

CREATE TABLE ofPubsubAffiliation (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  affiliation         VARCHAR2(10)   NOT NULL,
  CONSTRAINT ofPubsubAffiliation_pk PRIMARY KEY (serviceID, nodeID, jid)
);

CREATE TABLE ofPubsubItem (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  id                  VARCHAR2(100)  NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  creationDate        CHAR(15)       NOT NULL,
  payload             CLOB           NULL,
  CONSTRAINT ofPubsubItem_pk PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE ofPubsubSubscription (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  id                  VARCHAR2(100)  NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  owner               VARCHAR2(1024) NOT NULL,
  state               VARCHAR(15)    NOT NULL,
  deliver             INTEGER        NOT NULL,
  digest              INTEGER        NOT NULL,
  digest_frequency    INTEGER        NOT NULL,
  expire              CHAR(15)       NULL,
  includeBody         INTEGER        NOT NULL,
  showValues          VARCHAR(30)    NOT NULL,
  subscriptionType    VARCHAR(10)    NOT NULL,
  subscriptionDepth   INTEGER        NOT NULL,
  keyword             VARCHAR2(200)  NULL,
  CONSTRAINT ofPubsubSubscription_pk PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE ofPubsubDefaultConf (
  serviceID           VARCHAR2(100) NOT NULL,
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
  publisherModel      VARCHAR2(15)  NOT NULL,
  subscriptionEnabled INTEGER       NOT NULL,
  accessModel         VARCHAR2(10)  NOT NULL,
  language            VARCHAR2(255) NULL,
  replyPolicy         VARCHAR2(15)  NULL,
  associationPolicy   VARCHAR2(15)  NOT NULL,
  maxLeafNodes        INTEGER       NOT NULL,
  CONSTRAINT ofPubsubDefaultConf_pk PRIMARY KEY (serviceID, leaf)
);

-- Finally, insert default table values.

INSERT INTO ofID (idType, id) VALUES (18, 1);
INSERT INTO ofID (idType, id) VALUES (19, 1);
INSERT INTO ofID (idType, id) VALUES (23, 1);
INSERT INTO ofID (idType, id) VALUES (26, 2);
INSERT INTO ofID (idType, id) VALUES (27, 1);

INSERT INTO ofVersion (name, version) VALUES ('openfire', 32);

-- Entry for admin user
INSERT INTO ofUser (username, plainPassword, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');

-- Entry for default conference service
INSERT INTO ofMucService (serviceID, subdomain, isHidden) VALUES (1, 'conference', 0);

commit;
