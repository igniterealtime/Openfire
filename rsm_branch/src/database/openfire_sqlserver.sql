/* $Revision: 1650 $                                       */
/* $Date: 2005-07-20 00:18:17 -0300 (Wed, 20 Jul 2005) $   */

CREATE TABLE jiveUser (
  username              NVARCHAR(64)    NOT NULL,
  password              NVARCHAR(32),
  encryptedPassword     NVARCHAR(255),
  name                  NVARCHAR(100),
  email                 VARCHAR(100),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (username)
);
CREATE INDEX jiveUser_cDate_idx ON jiveUser (creationDate ASC);


CREATE TABLE jiveUserProp (
  username              NVARCHAR(64)    NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  propValue             NVARCHAR(2000)  NOT NULL,
  CONSTRAINT jiveUserProp_pk PRIMARY KEY (username, name)
);


CREATE TABLE jivePrivate (
  username              NVARCHAR(64)    NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  namespace             NVARCHAR(200)   NOT NULL,
  value                 NTEXT           NOT NULL,
  CONSTRAINT JivePrivate_pk PRIMARY KEY (username, name, namespace)
);


CREATE TABLE jiveOffline (
  username              NVARCHAR(64)    NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               NTEXT           NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID)
);


CREATE TABLE jivePresence (
  username              NVARCHAR(64)     NOT NULL,
  offlinePresence       NTEXT,
  offlineDate           CHAR(15)     NOT NULL,
  CONSTRAINT jivePresence_pk PRIMARY KEY (username)
);


CREATE TABLE jiveRoster (
  rosterID              INTEGER         NOT NULL,
  username              NVARCHAR(64)    NOT NULL,
  jid                   NVARCHAR(1024)  NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  NVARCHAR(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveRoster_username_idx ON jiveRoster (username ASC);


CREATE TABLE jiveRosterGroups (
  rosterID              INTEGER         NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             NVARCHAR(255)   NOT NULL,
  CONSTRAINT jiveRosterGroups_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRosterGroups_rosterid_idx ON jiveRosterGroups (rosterID ASC);
ALTER TABLE jiveRosterGroups ADD CONSTRAINT jiveRosterGroups_rosterID_fk FOREIGN KEY (rosterID) REFERENCES jiveRoster;


CREATE TABLE jiveVCard (
  username              NVARCHAR(64)    NOT NULL,
  value                 NTEXT           NOT NULL,
  CONSTRAINT JiveVCard_pk PRIMARY KEY (username)
);


CREATE TABLE jiveGroup (
  groupName             NVARCHAR(50)   NOT NULL,
  description           NVARCHAR(255),
  CONSTRAINT group_pk PRIMARY KEY (groupName)
);


CREATE TABLE jiveGroupProp (
   groupName            NVARCHAR(50)   NOT NULL,
   name                 NVARCHAR(100)   NOT NULL,
   propValue            NVARCHAR(2000)  NOT NULL,
   CONSTRAINT jiveGroupProp_pk PRIMARY KEY (groupName, name)
);


CREATE TABLE jiveGroupUser (
  groupName             NVARCHAR(50)    NOT NULL,
  username              NVARCHAR(100)   NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser_pk PRIMARY KEY (groupName, username, administrator)
);


CREATE TABLE jiveID (
  idType                INTEGER         NOT NULL,
  id                    INTEGER         NOT NULL,
  CONSTRAINT jiveID_pk PRIMARY KEY (idType)
);


CREATE TABLE jiveProperty (
  name        NVARCHAR(100) NOT NULL,
  propValue   NTEXT NOT NULL,
  CONSTRAINT jiveProperty_pk PRIMARY KEY (name)
);


CREATE TABLE jiveVersion (
  name     NVARCHAR(50) NOT NULL,
  version  INTEGER  NOT NULL,
  CONSTRAINT jiveVersion_pk PRIMARY KEY (name)
);

CREATE TABLE jiveExtComponentConf (
  subdomain             NVARCHAR(255)    NOT NULL,
  secret                NVARCHAR(255),
  permission            NVARCHAR(10)     NOT NULL,
  CONSTRAINT jiveExtComponentConf_pk PRIMARY KEY (subdomain)
);

CREATE TABLE jiveRemoteServerConf (
  domain                NVARCHAR(255)    NOT NULL,
  remotePort            INTEGER,
  permission            NVARCHAR(10)     NOT NULL,
  CONSTRAINT jiveRemoteServerConf_pk PRIMARY KEY (domain)
);

CREATE TABLE jivePrivacyList (
  username              NVARCHAR(64)    NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  isDefault             INT             NOT NULL,
  list                  NTEXT           NOT NULL,
  CONSTRAINT jivePrivacyList_pk PRIMARY KEY (username, name)
);
CREATE INDEX jivePList_default_idx ON jivePrivacyList (username, isDefault);

CREATE TABLE jiveSASLAuthorized (
  username        NVARCHAR(64)     NOT NULL,
  principal       NVARCHAR(2000)   NOT NULL,
  CONSTRAINT jiveSASLAuthoirzed_pk PRIMARY KEY (username, principal)
);

/* MUC Tables */

CREATE TABLE mucRoom (
  roomID              INT           NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                NVARCHAR(50)  NOT NULL,
  naturalName         NVARCHAR(255) NOT NULL,
  description         NVARCHAR(255),
  lockedDate          CHAR(15)      NOT NULL,
  emptyDate           CHAR(15)      NULL,
  canChangeSubject    INT           NOT NULL,
  maxUsers            INT           NOT NULL,
  publicRoom          INT           NOT NULL,
  moderated           INT           NOT NULL,
  membersOnly         INT           NOT NULL,
  canInvite           INT           NOT NULL,
  password            NVARCHAR(50)  NULL,
  canDiscoverJID      INT           NOT NULL,
  logEnabled          INT           NOT NULL,
  subject             NVARCHAR(100) NULL,
  rolesToBroadcast    INT           NOT NULL,
  useReservedNick     INT           NOT NULL,
  canChangeNick       INT           NOT NULL,
  canRegister         INT           NOT NULL,
  CONSTRAINT mucRoom__pk PRIMARY KEY (name)
);

CREATE INDEX mucRoom_roomID_idx on mucRoom(roomID);

CREATE TABLE mucRoomProp (
  roomID                INT             NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  propValue             NVARCHAR(2000)  NOT NULL,
  CONSTRAINT mucRoomProp_pk PRIMARY KEY (roomID, name)
);

CREATE TABLE mucAffiliation (
  roomID              INT            NOT NULL,
  jid                 NVARCHAR(424) NOT NULL,
  affiliation         INT            NOT NULL,
  CONSTRAINT mucAffiliation__pk PRIMARY KEY (roomID,jid)
);

CREATE TABLE mucMember (
  roomID              INT            NOT NULL,
  jid                 NVARCHAR(424) NOT NULL,
  nickname            NVARCHAR(255)  NULL,
  firstName           NVARCHAR(100)  NULL,
  lastName            NVARCHAR(100)  NULL,
  url                 NVARCHAR(100)  NULL,
  email               NVARCHAR(100)  NULL,
  faqentry            NVARCHAR(100)  NULL,
  CONSTRAINT mucMember__pk PRIMARY KEY (roomID,jid)
);

CREATE TABLE mucConversationLog (
  roomID              INT            NOT NULL,
  sender              NVARCHAR(1024) NOT NULL,
  nickname            NVARCHAR(255)  NULL,
  time                CHAR(15)       NOT NULL,
  subject             NVARCHAR(255)  NULL,
  body                NTEXT          NULL
);
CREATE INDEX mucLog_time_idx ON mucConversationLog (time);

/* PubSub Tables */

CREATE TABLE pubsubNode (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  leaf                INT            NOT NULL,
  creationDate        CHAR(15)       NOT NULL,
  modificationDate    CHAR(15)       NOT NULL,
  parent              NVARCHAR(100)  NULL,
  deliverPayloads     INT            NOT NULL,
  maxPayloadSize      INT            NULL,
  persistItems        INT            NULL,
  maxItems            INT            NULL,
  notifyConfigChanges INT            NOT NULL,
  notifyDelete        INT            NOT NULL,
  notifyRetract       INT            NOT NULL,
  presenceBased       INT            NOT NULL,
  sendItemSubscribe   INT            NOT NULL,
  publisherModel      NVARCHAR(15)   NOT NULL,
  subscriptionEnabled INT            NOT NULL,
  configSubscription  INT            NOT NULL,
  accessModel         NVARCHAR(10)   NOT NULL,
  payloadType         NVARCHAR(100)  NULL,
  bodyXSLT            NVARCHAR(100)  NULL,
  dataformXSLT        NVARCHAR(100)  NULL,
  creator             NVARCHAR(255)  NOT NULL,
  description         NVARCHAR(255)  NULL,
  language            NVARCHAR(255)  NULL,
  name                NVARCHAR(50)   NULL,
  replyPolicy         NVARCHAR(15)   NULL,
  associationPolicy   NVARCHAR(15)   NULL,
  maxLeafNodes        INT            NULL,
  CONSTRAINT pubsubNode_pk PRIMARY KEY (serviceID, nodeID)
);

CREATE TABLE pubsubNodeJIDs (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  jid                 NVARCHAR(250) NOT NULL,
  associationType     NVARCHAR(20)   NOT NULL,
  CONSTRAINT pubsubJID_pk PRIMARY KEY (serviceID, nodeID, jid)
);

CREATE TABLE pubsubNodeGroups (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  rosterGroup         NVARCHAR(100)  NOT NULL
);
CREATE INDEX pubsubNodeGroups_idx ON pubsubNodeGroups (serviceID, nodeID);

CREATE TABLE pubsubAffiliation (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  jid                 NVARCHAR(250)  NOT NULL,
  affiliation         NVARCHAR(10)   NOT NULL,
  CONSTRAINT pubsubAffil_pk PRIMARY KEY (serviceID, nodeID, jid)
);

CREATE TABLE pubsubItem (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  id                  NVARCHAR(100)  NOT NULL,
  jid                 NVARCHAR(1024) NOT NULL,
  creationDate        CHAR(15)       NOT NULL,
  payload             NTEXT          NULL,
  CONSTRAINT pubsubItem_pk PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubSubscription (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  id                  NVARCHAR(100)  NOT NULL,
  jid                 NVARCHAR(1024) NOT NULL,
  owner               NVARCHAR(1024) NOT NULL,
  state               NVARCHAR(15)   NOT NULL,
  deliver             INT            NOT NULL,
  digest              INT            NOT NULL,
  digest_frequency    INT            NOT NULL,
  expire              CHAR(15)       NULL,
  includeBody         INT            NOT NULL,
  showValues          NVARCHAR(30)   NOT NULL,
  subscriptionType    NVARCHAR(10)   NOT NULL,
  subscriptionDepth   INT            NOT NULL,
  keyword             NVARCHAR(200)  NULL,
  CONSTRAINT pubsubSubs_pk PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubDefaultConf (
  serviceID           NVARCHAR(100) NOT NULL,
  leaf                INT           NOT NULL,
  deliverPayloads     INT           NOT NULL,
  maxPayloadSize      INT           NOT NULL,
  persistItems        INT           NOT NULL,
  maxItems            INT           NOT NULL,
  notifyConfigChanges INT           NOT NULL,
  notifyDelete        INT           NOT NULL,
  notifyRetract       INT           NOT NULL,
  presenceBased       INT           NOT NULL,
  sendItemSubscribe   INT           NOT NULL,
  publisherModel      NVARCHAR(15)  NOT NULL,
  subscriptionEnabled INT           NOT NULL,
  accessModel         NVARCHAR(10)  NOT NULL,
  language            NVARCHAR(255) NULL,
  replyPolicy         NVARCHAR(15)  NULL,
  associationPolicy   NVARCHAR(15)  NOT NULL,
  maxLeafNodes        INT           NOT NULL,
  CONSTRAINT pubsubDefConf_pk PRIMARY KEY (serviceID, leaf)
);

/* Finally, insert default table values. */

INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
INSERT INTO jiveID (idType, id) VALUES (23, 1);

INSERT INTO jiveVersion (name, version) VALUES ('openfire', 11);

/* Entry for admin user */
INSERT INTO jiveUser (username, password, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');