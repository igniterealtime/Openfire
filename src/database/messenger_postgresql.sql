-- $RCSfile$
-- $Revision$
-- $Date$

-- Note: This schema has only been tested on PostgreSQL 7.3.2.

CREATE TABLE jiveUser (
  userID                INTEGER         NOT NULL,
  password              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100),
  nameVisible           INTEGER         NOT NULL,
  email                 VARCHAR(100),
  emailVisible          INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (userID)
);
CREATE INDEX jiveUser_cDate_idx ON jiveUser (creationDate);


CREATE TABLE jiveUserProp (
  userID                INTEGER         NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             TEXT            NOT NULL,
  CONSTRAINT jiveUserProp_pk PRIMARY KEY (userID, name)
);
-- Commented out since this will cause a third party user system to break
-- If you do not use a third part user system, uncomment these constraints
-- ALTER TABLE jiveUserProp ADD CONSTRAINT jiveUserProp_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jivePrivate (
  userID                INTEGER         NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(200)    NOT NULL,
  value                 TEXT            NOT NULL,
  CONSTRAINT jivePrivate_pk PRIMARY KEY (userID, name, namespace)
);
-- Commented out since this will cause a third party user system to break
-- If you do not use a third part user system, uncomment these constraints
-- ALTER TABLE jivePrivate ADD CONSTRAINT jivePrivate_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveOffline (
  userID                INTEGER         NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               LONG            NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (userID, messageID)
);
-- Commented out since this will cause a third party user system to break
-- If you do not use a third part user system, uncomment these constraints
-- ALTER TABLE jiveRoster ADD CONSTRAINT jiveRoster_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveRoster (
  rosterID              INTEGER         NOT NULL,
  userID                INTEGER         NOT NULL,
  jid                   TEXT            NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  VARCHAR(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveRoster_userID_idx ON jiveRoster (userID);
-- Commented out since this will cause a third party user system to break
-- If you do not use a third part user system, uncomment these constraints
-- ALTER TABLE jiveRoster ADD CONSTRAINT jiveRoster_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveRosterGroups (
  rosterID              INTEGER         NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             VARCHAR(255)    NOT NULL,
  CONSTRAINT jiveRosterGroups_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRosterGroups_rosterID_idx ON jiveRosterGroups (rosterID);
ALTER TABLE jiveRosterGroups ADD CONSTRAINT jiveRosterGroups_rosterID_fk FOREIGN KEY (rosterID) REFERENCES jiveRoster INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveVCard (
  userID                INTEGER         NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             TEXT            NOT NULL,
  CONSTRAINT jiveVCard_pk PRIMARY KEY (userID, name)
);
-- Commented out since this will cause a third party user system to break
-- If you do not use a third part user system, uncomment these constraints
-- ALTER TABLE jiveVCard ADD CONSTRAINT jiveVCard_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveDomain (
  domainID              INTEGER         NOT NULL,
  name                  VARCHAR(100)    UNIQUE NOT NULL,
  description           VARCHAR(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveDomain_pk PRIMARY KEY (domainID)
);


CREATE TABLE jiveChatbot (
  chatbotID             INTEGER         NOT NULL,
  description           VARCHAR(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveChatbot_pk PRIMARY KEY (chatbotID)
);


CREATE TABLE jiveUserID (
  username              VARCHAR(30)      UNIQUE NOT NULL,
  domainID              INTEGER          NOT NULL,
  objectType            INTEGER          NOT NULL,
  objectID              INTEGER          NOT NULL,
  CONSTRAINT jiveUserID_pk PRIMARY KEY (username, domainID)
);
CREATE INDEX jiveUser_object_idx ON jiveUserID (objectType, objectID);


CREATE TABLE jiveGroup (
  groupID               INTEGER         NOT NULL,
  name                  VARCHAR(50)     NOT NULL,
  description           VARCHAR(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveGroup_pk PRIMARY KEY (groupID)
);
CREATE INDEX jiveGroup_cDate_idx ON jiveGroup (creationDate);
CREATE INDEX jiveGroup_name_idx ON jiveGroup (name);


CREATE TABLE jiveGroupProp (
  groupID               INTEGER         NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             TEXT            NOT NULL,
  CONSTRAINT jiveGroupProp_pk PRIMARY KEY (groupID, name)
);
-- Commented out since this will cause a third party user system to break
-- If you do not use a third part user system, uncomment these constraints
-- ALTER TABLE jiveGroupProp ADD CONSTRAINT jiveGroupProp_groupID_fk FOREIGN KEY (groupID) REFERENCES jiveGroup INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveGroupUser (
  groupID               INTEGER         NOT NULL,
  userID                INTEGER         NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser_pk PRIMARY KEY (groupID, userID, administrator)
);
-- Commented out since this will cause a third party user system to break
-- If you do not use a third part user system, uncomment these constraints
-- ALTER TABLE jiveGroupUser ADD CONSTRAINT jiveGroupUser_groupID_fk FOREIGN KEY (groupID) REFERENCES jiveGroup INITIALLY DEFERRED DEFERRABLE;
-- ALTER TABLE jiveGroupUser ADD CONSTRAINT jiveGroupUser_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser INITIALLY DEFERRED DEFERRABLE;


CREATE TABLE jiveID (
  idType                INTEGER         NOT NULL,
  id                    INTEGER         NOT NULL,
  CONSTRAINT jiveID_pk PRIMARY KEY (idType)
);

-- MUC Tables

CREATE TABLE mucRoom (
  roomID              INTEGER       NOT NULL,
  name                VARCHAR(50)   NULL,
  description         VARCHAR(255),
  canChangeSubject    INTEGER       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          INTEGER       NOT NULL,
  moderated           INTEGER       NOT NULL,
  invitationRequired  INTEGER       NOT NULL,
  canInvite           INTEGER       NOT NULL,
  passwordProtected   INTEGER       NOT NULL,
  password            VARCHAR(50)   NULL,
  canDiscoverJID      INTEGER       NOT NULL,
  logEnabled          INTEGER       NOT NULL,
  subject             VARCHAR(100)  NULL,
  rolesToBroadcast    INTEGER       NOT NULL,
  inMemory            INTEGER       NOT NULL,
  CONSTRAINT mucRoom__pk PRIMARY KEY (name)
);

CREATE INDEX mucRoom_roomID_idx ON mucRoom(roomID);

CREATE TABLE mucAffiliation (
  roomID              INTEGER        NOT NULL,
  jid                 VARCHAR(3000)  NOT NULL,
  affiliation         INTEGER        NOT NULL,
  CONSTRAINT mucAffiliation__pk PRIMARY KEY (roomID,jid)
);

CREATE TABLE mucMember (
  roomID              INTEGER        NOT NULL,
  jid                 TEXT           NOT NULL,
  nickname            VARCHAR(255)   NULL,
  CONSTRAINT mucMember__pk PRIMARY KEY (roomID,jid)
);

CREATE TABLE mucConversationLog (
  roomID              INTEGER        NOT NULL,
  sender              TEXT           NOT NULL,
  nickname            VARCHAR(255)   NULL,
  time                CHAR(15)       NOT NULL,
  subject             VARCHAR(255)   NULL,
  body                TEXT           NULL
);

-- Finally, insert default table values.

-- Unique ID entry for user, group   
-- The User ID entry starts at 2 (after admin user entry).  
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

-- Entry for admin user -- password is "admin"
INSERT INTO jiveUserID (username, domainID, objectType, objectID) VALUES ('admin', 1, 0, 1);
INSERT INTO jiveUser (userID, name, password, email, emailVisible, nameVisible, creationDate, modificationDate)
    VALUES (1, 'Administrator', 'admin', 'admin@example.com', 1, 1, '0', '0');

-- Make the administrator an admin member of the Administrators group
INSERT INTO jiveGroup (groupID, name, description, creationDate, modificationDate)
    VALUES (1, 'Administrators', 'Messenger Server administrators', '0', '0');
INSERT INTO jiveGroupUser (groupID, userID, administrator) VALUES (1, 1, 1);
