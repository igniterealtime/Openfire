// $RCSfile$
// $Revision$
// $Date$

CREATE TABLE jiveUser (
  userID                BIGINT          NOT NULL,
  password              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100),
  nameVisible           INTEGER         NOT NULL,
  email                 VARCHAR(100),
  emailVisible          INTEGER         NOT NULL,
  creationDate          VARCHAR(15)     NOT NULL,
  modificationDate      VARCHAR(15)     NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (userID)
);
CREATE INDEX jiveUser_cDate_idx ON jiveUser (creationDate);


CREATE TABLE jiveUserProp (
  userID                BIGINT          NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(4000)   NOT NULL,
  CONSTRAINT jiveUserProp_pk PRIMARY KEY (userID, name)
);


CREATE TABLE jivePrivate (
  userID                BIGINT          NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(200)    NOT NULL,
  value                 LONGVARCHAR     NOT NULL,
  CONSTRAINT jivePrivate_pk PRIMARY KEY (userID, name, namespace)
);


CREATE TABLE jiveOffline (
  userID                BIGINT          NOT NULL,
  messageID             BIGINT          NOT NULL,
  creationDate          VARCHAR(15)     NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               LONGVARCHAR     NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (userID, messageID)
);


CREATE TABLE jiveRoster (
  rosterID              BIGINT          NOT NULL,
  userID                BIGINT          NOT NULL,
  jid                   VARCHAR(3071)   NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  VARCHAR(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveRoster_userid_idx ON jiveRoster (userID);


CREATE TABLE jiveRosterGroups (
  rosterID              BIGINT          NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             VARCHAR(255)    NOT NULL,
  CONSTRAINT jiveRosterGroups_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRosterGroup_rosterid_idx ON jiveRosterGroups (rosterID);


CREATE TABLE jiveVCard (
  userID                BIGINT          NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(4000)   NOT NULL,
  CONSTRAINT jiveVCard_pk PRIMARY KEY (userID, name)
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


CREATE TABLE jiveChatbot (
  chatbotID             BIGINT          NOT NULL,
  description           VARCHAR(255),
  creationDate          VARCHAR(15)     NOT NULL,
  modificationDate      VARCHAR(15)     NOT NULL,
  CONSTRAINT jiveChatbot_pk PRIMARY KEY (chatbotID)
);


CREATE TABLE jiveUserID (
  username              VARCHAR(30)     NOT NULL,
  domainID              BIGINT          NOT NULL,
  objectType            INTEGER         NOT NULL,
  objectID              BIGINT          NOT NULL,
  CONSTRAINT jiveUserID_pk PRIMARY KEY (username, domainID),
  CONSTRAINT jiveUserID_username UNIQUE (username)
);
CREATE INDEX jiveUserID_object_idx ON jiveUserID (objectType, objectID);


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
  userID                BIGINT          NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser_pk PRIMARY KEY (groupID, userID, administrator)
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
INSERT INTO jiveUserID (username, domainID, objectType, objectID) VALUES ('admin', 1, 0, 1);
INSERT INTO jiveUser (userID, name, password, email, emailVisible, nameVisible, creationDate, modificationDate)
    VALUES (1, 'Administrator', 'admin', 'admin@example.com', 1, 1, '0', '0');

// Make the administrator an admin member of the Administrators group
INSERT INTO jiveGroup (groupID, name, description, creationDate, modificationDate)
    VALUES (1, 'Administrators', 'Messenger Server administrators', '0', '0');
INSERT INTO jiveGroupUser (groupID, userID, administrator) VALUES (1, 1, 1);
