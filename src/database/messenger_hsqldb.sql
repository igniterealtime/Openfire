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
  username              VARHCAR(32)     NOT NULL,
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
  jid                   VARCHAR(3071)   NOT NULL,
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


CREATE TABLE jiveDomain (
  domainID              BIGINT          NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  description           VARCHAR(255),
  creationDate          VARCHAR(15)     NOT NULL,
  modificationDate      VARCHAR(15)     NOT NULL,
  CONSTRAINT jiveDomain_pk PRIMARY KEY (domainID),
  CONSTRAINT jiveDomain_name UNIQUE (name)
);


CREATE TABLE jiveGroup (
  groupID               BIGINT          NOT NULL,
  name                  VARCHAR(50)     NOT NULL,
  description           VARCHAR(255),
  creationDate          VARCHAR(15)     NOT NULL,
  modificationDate      VARCHAR(15)     NOT NULL,
  CONSTRAINT jiveGroup_pk PRIMARY KEY (groupID)
);
CREATE INDEX jiveGroup_cDate_idx ON jiveGroup (creationDate);
CREATE INDEX jiveGroup_name_idx ON jiveGroup (name);


CREATE TABLE jiveGroupProp (
  groupID               BIGINT          NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(4000)   NOT NULL,
  CONSTRAINT jiveGroupProp_pk PRIMARY KEY (groupID, name)
);


CREATE TABLE jiveGroupUser (
  groupID               BIGINT          NOT NULL,
  username              BIGINT          NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser_pk PRIMARY KEY (groupID, username, administrator)
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
  canChangeSubject    INTEGER       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          INTEGER       NOT NULL,
  moderated           INTEGER       NOT NULL,
  invitationRequired  INTEGER       NOT NULL,
  canInvite           INTEGER       NOT NULL,
  password            VARCHAR(50)   NULL,
  canDiscoverJID      INTEGER       NOT NULL,
  logEnabled          INTEGER       NOT NULL,
  subject             VARCHAR(100)  NULL,
  rolesToBroadcast    INTEGER       NOT NULL,
  lastActiveDate      CHAR(15)      NULL,
  inMemory            INTEGER       NOT NULL,
  PRIMARY KEY (name)
);

CREATE INDEX mucRoom_roomid_idx ON mucRoom(roomID);

CREATE TABLE mucAffiliation (
  roomID              BIGINT        NOT NULL,
  jid                 VARCHAR(3071) NOT NULL,
  affiliation         INTEGER       NOT NULL,
  PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucMember (
  roomID              BIGINT        NOT NULL,
  jid                 VARCHAR(3071) NOT NULL,
  nickname            VARCHAR(255)  NULL,
  PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucConversationLog (
  roomID              BIGINT        NOT NULL,
  sender              VARCHAR(3071) NOT NULL,
  nickname            VARCHAR(255)  NULL,
  time                CHAR(15)      NOT NULL,
  subject             VARCHAR(255)  NULL,
  body                LONGVARCHAR   NULL
);

// Finally, insert default table values.

// Unique ID entry for user, group
// The User ID entry starts at 2 (after admin user entry).
INSERT INTO jiveID (idType, id) VALUES (0, 1);
INSERT INTO jiveID (idType, id) VALUES (1, 1);
INSERT INTO jiveID (idType, id) VALUES (2, 1);
INSERT INTO jiveID (idType, id) VALUES (3, 2);
INSERT INTO jiveID (idType, id) VALUES (4, 2);
INSERT INTO jiveID (idType, id) VALUES (13, 1);
INSERT INTO jiveID (idType, id) VALUES (14, 2);
INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
INSERT INTO jiveID (idType, id) VALUES (23, 1);

// Entry for admin user -- password is "admin"
INSERT INTO jiveUser (username, password, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');