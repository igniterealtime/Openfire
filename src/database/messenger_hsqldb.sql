// $RCSfile$
// $Revision$
// $Date$

CREATE TABLE jiveUser (
  username              VARCHAR(32)     NOT NULL,
  password              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100),
  email                 VARCHAR(100),
  creationDate          VARCHAR(15)     NOT NULL,
  modificationDate      VARCHAR(15)     NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (username)
);
CREATE INDEX jiveUser_cDate_idx ON jiveUser (creationDate);


CREATE TABLE jiveUserProp (
  username              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(4000)   NOT NULL,
  CONSTRAINT jiveUserProp_pk PRIMARY KEY (username, name)
);


CREATE TABLE jivePrivate (
  username              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(200)    NOT NULL,
  value                 LONGVARCHAR     NOT NULL,
  CONSTRAINT jivePrivate_pk PRIMARY KEY (username, name, namespace)
);


CREATE TABLE jiveOffline (
  username              VARCHAR(32)     NOT NULL,
  messageID             BIGINT          NOT NULL,
  creationDate          VARCHAR(15)     NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               LONGVARCHAR     NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID)
);


CREATE TABLE jiveRoster (
  rosterID              BIGINT          NOT NULL,
  username              VARCHAR(32)     NOT NULL,
  jid                   VARCHAR(1024)   NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  VARCHAR(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveRoster_username_idx ON jiveRoster (username);


CREATE TABLE jiveRosterGroups (
  rosterID              BIGINT          NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             VARCHAR(255)    NOT NULL,
  CONSTRAINT jiveRosterGroups_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRosterGroup_rosterid_idx ON jiveRosterGroups (rosterID);


CREATE TABLE jiveVCard (
  username              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(4000)   NOT NULL,
  CONSTRAINT jiveVCard_pk PRIMARY KEY (username, name)
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
  username              VARCHAR(32)     NOT NULL,
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

// MUC Tables

CREATE TABLE mucRoom (
  roomID              BIGINT        NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  naturalName         VARCHAR(255)  NOT NULL,
  description         VARCHAR(255),
  lockedDate          CHAR(15)      NOT NULL,
  canChangeSubject    INTEGER       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          INTEGER       NOT NULL,
  moderated           INTEGER       NOT NULL,
  membersOnly         INTEGER       NOT NULL,
  canInvite           INTEGER       NOT NULL,
  password            VARCHAR(50)   NULL,
  canDiscoverJID      INTEGER       NOT NULL,
  logEnabled          INTEGER       NOT NULL,
  subject             VARCHAR(100)  NULL,
  rolesToBroadcast    INTEGER       NOT NULL,
  CONSTRAINT mucRoom_pk PRIMARY KEY (name)
);

CREATE INDEX mucRoom_roomid_idx ON mucRoom(roomID);

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
  time                CHAR(15)      NOT NULL,
  subject             VARCHAR(255)  NULL,
  body                LONGVARCHAR   NULL
);
CREATE INDEX mucLog_time_idx ON mucConversationLog (time);

// Finally, insert default table values.

INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
INSERT INTO jiveID (idType, id) VALUES (23, 1);

// Entry for admin user
INSERT INTO jiveUser (username, password, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');