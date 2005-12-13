/* $RCSfile$ */
/* $Revision: 1650 $                          */
/* $Date: 2005-07-19 20:18:17 -0700 (Tue, 19 Jul 2005) $               */

CREATE TABLE jiveUser (
  username              NVARCHAR(32)    NOT NULL,
  password              NVARCHAR(32)    NOT NULL,
  name                  NVARCHAR(100),
  email                 VARCHAR(100),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (username)
);
CREATE INDEX jiveUser_cDate_idx ON jiveUser (creationDate ASC);


CREATE TABLE jiveUserProp (
  username              NVARCHAR(32)    NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  propValue             TEXT            NOT NULL,
  CONSTRAINT jiveUserProp_pk PRIMARY KEY (username, name)
);


CREATE TABLE jivePrivate (
  username              NVARCHAR(32)    NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  namespace             NVARCHAR(200)   NOT NULL,
  value                 TEXT            NOT NULL,
  CONSTRAINT JivePrivate_pk PRIMARY KEY (username, name, namespace)
);


CREATE TABLE jiveOffline (
  username              NVARCHAR(32)    NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               TEXT            NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID)
);


CREATE TABLE jiveRoster (
  rosterID              INTEGER         NOT NULL,
  username              NVARCHAR(32)    NOT NULL,
  jid                   TEXT            NOT NULL,
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
  username              NVARCHAR(32)    NOT NULL,
  value                 TEXT            NOT NULL,
  CONSTRAINT JiveVCard_pk PRIMARY KEY (username)
);


CREATE TABLE jiveGroup (
  groupName             NVARCHAR(50)   NOT NULL,
  description           NVARCHAR(255),
  CONSTRAINT group_pk PRIMARY KEY (groupName)
);


CREATE TABLE jiveGroupProp (
   groupName            NVARCHAR(50)   NOT NULL,
   name                 NVARCHAR(100)  NOT NULL,
   propValue            TEXT           NOT NULL,
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
  name         NVARCHAR(100) NOT NULL,
  propValue    TEXT NOT NULL,
  CONSTRAINT jiveProperty_pk PRIMARY KEY (name)
);


CREATE TABLE jiveVersion (
  majorVersion  INTEGER  NOT NULL,
  minorVersion  INTEGER  NOT NULL
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
  propValue             TEXT            NOT NULL,
  CONSTRAINT mucRoomProp_pk PRIMARY KEY (roomID, name)
);

CREATE TABLE mucAffiliation (
  roomID              INT            NOT NULL,
  jid                 VARCHAR(255)   NOT NULL,
  affiliation         INT            NOT NULL,
  CONSTRAINT mucAffiliation__pk PRIMARY KEY (roomID,jid)
);

CREATE TABLE mucMember (
  roomID              INT            NOT NULL,
  jid                 NVARCHAR(255)  NOT NULL,
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
  sender              TEXT           NOT NULL,
  nickname            NVARCHAR(255)  NULL,
  time                CHAR(15)       NOT NULL,
  subject             NVARCHAR(255)  NULL,
  body                TEXT           NULL
);
CREATE INDEX mucLog_time_idx ON mucConversationLog (time);

/* Finally, insert default table values. */

INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
INSERT INTO jiveID (idType, id) VALUES (23, 1);

INSERT INTO jiveVersion (majorVersion, minorVersion) VALUES (2, 2);

/* Entry for admin user */
INSERT INTO jiveUser (username, password, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');