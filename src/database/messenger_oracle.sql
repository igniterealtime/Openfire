REM // $RCSfile$
REM // $Revision$
REM // $Date$

CREATE TABLE jiveUser (
  username              VARCHAR2(32)     NOT NULL,
  password              VARCHAR2(32)    NOT NULL,
  name                  VARCHAR2(100),
  email                 VARCHAR2(100),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (username)
);
CREATE INDEX jiveUser_cDate_idx ON jiveUser (creationDate ASC);


CREATE TABLE jiveUserProp (
  username              VARCHAR2(32)    NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(1024)  NOT NULL,
  CONSTRAINT jiveUserProp_pk PRIMARY KEY (username, name)
);


CREATE TABLE jivePrivate (
  username              VARCHAR2(32)    NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  namespace             VARCHAR2(200)   NOT NULL,
  value                 LONG            NOT NULL,
  CONSTRAINT jivePrivate_pk PRIMARY KEY (username, name, namespace)
);


CREATE TABLE jiveOffline (
  username              VARCHAR2(32)    NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               LONG            NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID)
);


CREATE TABLE jiveRoster (
  rosterID              INTEGER         NOT NULL,
  username              VARCHAR2(32)    NOT NULL,
  jid                   VARCHAR2(1024)  NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  VARCHAR2(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveRoster_username_idx ON jiveRoster (username ASC);


CREATE TABLE jiveRosterGroups (
  rosterID              INTEGER         NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             VARCHAR2(255)   NOT NULL,
  CONSTRAINT jiveRosterGroups_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRosterGroup_rosterid_idx ON jiveRosterGroups (rosterID ASC);
ALTER TABLE jiveRosterGroups ADD CONSTRAINT jiveRosterGroups_rosterID_fk FOREIGN KEY (rosterID) REFERENCES jiveRoster INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveVCard (
  username              VARCHAR2(32)    NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(4000)  NOT NULL,
  CONSTRAINT JiveVCard_pk PRIMARY KEY (username, name)
);

CREATE TABLE jiveGroup (
  groupName             VARCHAR2(50)    NOT NULL,
  description           VARCHAR2(255),
  CONSTRAINT jiveGroup_pk PRIMARY KEY (groupName)
);

CREATE TABLE jiveGroupProp (
  groupName             VARCHAR(50)     NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(4000)  NOT NULL,
  CONSTRAINT jiveGroupProp_pk PRIMARY KEY (groupName, name)
);

CREATE TABLE jiveGroupUser (
  groupName             VARCHAR(50)     NOT NULL,
  username              VARCHAR2(32)    NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser PRIMARY KEY (groupName, username, administrator)
);

CREATE TABLE jiveID (
  idType                INTEGER         NOT NULL,
  id                    INTEGER         NOT NULL,
  CONSTRAINT jiveID_pk PRIMARY KEY (idType)
);

CREATE TABLE jiveProperty (
  name        VARCHAR2(100) NOT NULL,
  propValue   VARCHAR2(4000) NOT NULL,
  CONSTRAINT jiveProperty_pk PRIMARY KEY (name)
);

CREATE TABLE jiveVersion (
  majorVersion  INTEGER  NOT NULL,
  minorVersion  INTEGER  NOT NULL
);

CREATE TABLE jiveExtComponentConf (
  subdomain             VARCHAR2(255)    NOT NULL,
  secret                VARCHAR2(255),
  permission            VARCHAR2(10)     NOT NULL,
  CONSTRAINT jiveExtComponentConf_pk PRIMARY KEY (subdomain)
);

CREATE TABLE jiveRemoteServerConf (
  domain                VARCHAR2(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR2(10)     NOT NULL,
  CONSTRAINT jiveRemoteServerConf_pk PRIMARY KEY (domain)
);

REM // MUC Tables

CREATE TABLE mucRoom(
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
  password            VARCHAR2(50)  NULL,
  canDiscoverJID      INTEGER       NOT NULL,
  logEnabled          INTEGER       NOT NULL,
  subject             VARCHAR2(100) NULL,
  rolesToBroadcast    INTEGER       NOT NULL,
  useReservedNick     INTEGER       NOT NULL,
  canChangeNick       INTEGER       NOT NULL,
  canRegister         INTEGER       NOT NULL,
  CONSTRAINT mucRoom_pk PRIMARY KEY (name)
);
CREATE INDEX mucRoom_roomid_idx ON mucRoom (roomID);

CREATE TABLE mucRoomProp (
  roomID                INT             NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(1024)  NOT NULL,
  CONSTRAINT mucRoomProp_pk PRIMARY KEY (roomID, name)
);

CREATE TABLE mucAffiliation (
  roomID              INT            NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  affiliation         INTEGER        NOT NULL,
  CONSTRAINT mucAffiliation_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucMember (
  roomID              INT            NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  nickname            VARCHAR2(255)  NULL,
  firstName           VARCHAR2(100)  NULL,
  lastName            VARCHAR2(100)  NULL,
  url                 VARCHAR2(100)  NULL,
  email               VARCHAR2(100)  NULL,
  faqentry            VARCHAR2(100)  NULL,
  CONSTRAINT mucMember_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucConversationLog (
  roomID              INT            NOT NULL,
  sender              VARCHAR2(1024) NOT NULL,
  nickname            VARCHAR2(255)  NULL,
  time                CHAR(15)       NOT NULL,
  subject             VARCHAR2(255)  NULL,
  body                VARCHAR2(4000) NULL
);
CREATE INDEX mucLog_time_idx ON mucConversationLog (time);

REM // Finally, insert default table values.

INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
INSERT INTO jiveID (idType, id) VALUES (23, 1);

INSERT INTO jiveVersion (majorVersion, minorVersion) VALUES (2, 2);

REM // Entry for admin user
INSERT INTO jiveUser (username, password, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');