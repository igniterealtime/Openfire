REM // $RCSfile$
REM // $Revision$
REM // $Date$

CREATE TABLE jiveUser (
  userID                INTEGER         NOT NULL,
  password              VARCHAR2(32)    NOT NULL,
  name                  VARCHAR2(100),
  nameVisible           INTEGER         NOT NULL,
  email                 VARCHAR2(100),
  emailVisible          INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (userID)
);
CREATE INDEX jiveUser_cDate_idx ON jiveUser (creationDate ASC);


CREATE TABLE jiveUserProp (
  userID                INTEGER         NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(4000)  NOT NULL,
  CONSTRAINT jiveUserProp_pk PRIMARY KEY (userID, name)
);
REM Commented out since this will cause a third party user system to break
REM If you do not use a third part user system, uncomment these constraints
REM ALTER TABLE jiveUserProp ADD CONSTRAINT jiveUserProp_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jivePrivate (
  userID                INTEGER         NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  namespace             VARCHAR2(200)   NOT NULL,
  value                 LONG            NOT NULL,
  CONSTRAINT jivePrivate_pk PRIMARY KEY (userID, name, namespace)
);
REM Commented out since this will cause a third party user system to break
REM If you do not use a third part user system, uncomment these constraints
REM ALTER TABLE jivePrivate ADD CONSTRAINT jivePrivate_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveOffline (
  userID                INTEGER         NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               LONG            NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (userID, messageID)
);
REM Commented out since this will cause a third party user system to break
REM If you do not use a third part user system, uncomment these constraints
REM ALTER TABLE jiveRoster ADD CONSTRAINT jiveRoster_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveRoster (
  rosterID              INTEGER         NOT NULL,
  userID                INTEGER         NOT NULL,
  jid                   VARCHAR2(4000)  NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  VARCHAR2(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveRoster_userid_idx ON jiveRoster (userID ASC);
REM Commented out since this will cause a third party user system to break
REM If you do not use a third part user system, uncomment these constraints
REM ALTER TABLE jiveRoster ADD CONSTRAINT jiveRoster_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveRosterGroups (
  rosterID              INTEGER         NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             VARCHAR2(255)   NOT NULL,
  CONSTRAINT jiveRosterGroups_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRosterGroup_rosterid_idx ON jiveRosterGroups (rosterID ASC);
ALTER TABLE jiveRosterGroups ADD CONSTRAINT jiveRosterGroups_rosterID_fk FOREIGN KEY (rosterID) REFERENCES jiveRoster INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveVCard (
  userID                INTEGER         NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(4000)  NOT NULL,
  CONSTRAINT JiveVCard_pk PRIMARY KEY (userID, name)
);
REM Commented out since this will cause a third party user system to break
REM If you do not use a third part user system, uncomment these constraints
REM ALTER TABLE jiveVCard ADD CONSTRAINT jiveVCard_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveDomain (
  domainID              INTEGER         NOT NULL,
  name                  VARCHAR2(100)   UNIQUE NOT NULL,
  description           VARCHAR2(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveDomain_pk PRIMARY KEY (domainID)
);


CREATE TABLE jiveChatbot (
  chatbotID             INTEGER         NOT NULL,
  description           VARCHAR2(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveChatbot_pk PRIMARY KEY (chatbotID)
);


CREATE TABLE jiveUserID (
  username              VARCHAR2(30)     UNIQUE NOT NULL,
  domainID              INTEGER          NOT NULL,
  objectType            INTEGER          NOT NULL,
  objectID              INTEGER          NOT NULL,
  CONSTRAINT jiveUserID_pk PRIMARY KEY (username, domainID)
);
CREATE INDEX jiveUserID_object_idx ON jiveUserID (objectType, objectID);


CREATE TABLE jiveGroup (
  groupID               INTEGER         NOT NULL,
  name                  VARCHAR2(50)    NOT NULL,
  description           VARCHAR2(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveGroup_pk PRIMARY KEY (groupID)
);
CREATE INDEX jiveGroup_cDate_idx ON jiveGroup (creationDate ASC);
CREATE INDEX jiveGroup_name_idx ON jiveGroup (name);


CREATE TABLE jiveGroupProp (
  groupID               INTEGER         NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(4000)  NOT NULL,
  CONSTRAINT jiveGroupProp_pk PRIMARY KEY (groupID, name)
);
REM Commented out since this will cause a third party user system to break
REM If you do not use a third part user system, uncomment these constraints
REM ALTER TABLE jiveGroupProp ADD CONSTRAINT jiveGroupProp_groupID_fk FOREIGN KEY (groupID) REFERENCES jiveGroup INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveGroupUser (
  groupID               INTEGER         NOT NULL,
  userID                INTEGER         NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser PRIMARY KEY (groupID, userID, administrator)
);
REM Commented out since this will cause a third party user system to break
REM If you do not use a third part user system, uncomment these constraints
REM ALTER TABLE jiveGroupUser ADD CONSTRAINT jiveGroupUser_groupID_fk FOREIGN KEY (groupID) REFERENCES jiveGroup INITIALLY DEFERRED DEFERRABLE;
REM ALTER TABLE jiveGroupUser ADD CONSTRAINT jiveGroupUser_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


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

REM // MUC Tables

CREATE TABLE mucRoom(
  roomID              INT           NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR2(50)  NOT NULL,
  naturalName         VARCHAR2(255) NOT NULL,
  description         VARCHAR2(255),
  canChangeSubject    INTEGER       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          INTEGER       NOT NULL,
  moderated           INTEGER       NOT NULL,
  invitationRequired  INTEGER       NOT NULL,
  canInvite           INTEGER       NOT NULL,
  password            VARCHAR2(50)  NULL,
  canDiscoverJID      INTEGER       NOT NULL,
  logEnabled          INTEGER       NOT NULL,
  subject             VARCHAR2(100) NULL,
  rolesToBroadcast    INTEGER       NOT NULL,
  lastActiveDate      CHAR(15)      NULL,
  inMemory            INTEGER       NOT NULL,
  CONSTRAINT mucRoom_pk PRIMARY KEY (name)
);
CREATE INDEX mucRoom_roomid_idx ON mucRoom (roomID);

CREATE TABLE mucAffiliation (
  roomID              INT            NOT NULL,
  jid                 VARCHAR2(4000) NOT NULL,
  affiliation         INTEGER        NOT NULL,
  CONSTRAINT mucAffiliation_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucMember (
  roomID              INT            NOT NULL,
  jid                 VARCHAR2(4000) NOT NULL,
  nickname            VARCHAR2(255)  NULL,
  CONSTRAINT mucMember_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucConversationLog (
  roomID              INT            NOT NULL,
  sender              VARCHAR2(4000) NOT NULL,
  nickname            VARCHAR2(255)  NULL,
  time                CHAR(15)       NOT NULL,
  subject             VARCHAR2(255)  NULL,
  body                VARCHAR2(4000) NULL
);

REM // Finally, insert default table values.

REM // Unique ID entry for user, group
REM // The User ID entry starts at 2 (after admin user entry).
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

REM // Entry for admin user -- password is "admin"
INSERT INTO jiveUserID (username, domainID, objectType, objectID) VALUES ('admin', 1, 0, 1);
INSERT INTO jiveUser (userID, name, password, email, emailVisible, nameVisible, creationDate, modificationDate)
    VALUES (1, 'Administrator', 'admin', 'admin@example.com', 1, 1, '0', '0');

REM // Make the administrator an admin member of the Administrators group
INSERT INTO jiveGroup (groupID, name, description, creationDate, modificationDate)
    VALUES (1, 'Administrators', 'Messenger Server administrators', '0', '0');
INSERT INTO jiveGroupUser (groupID, userID, administrator) VALUES (1, 1, 1);
