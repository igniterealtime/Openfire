/*
 * $RCSfile$
 * $Revision$
 * $Date$
 */

CREATE TABLE jiveUser (
  userID                INTEGER         NOT NULL,
  password              NVARCHAR(32)    NOT NULL,
  name                  NVARCHAR(100),
  nameVisible           INTEGER         NOT NULL,
  email                 VARCHAR(100),
  emailVisible          INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (userID)
);
CREATE INDEX jiveUser_cDate_idx ON jiveUser (creationDate ASC);


CREATE TABLE jiveUserProp (
  userID                INTEGER         NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  propValue             NVARCHAR(3900)  NOT NULL,
  CONSTRAINT jiveUserProp_pk PRIMARY KEY (userID, name)
);
/* Commented out since this will cause a third party user system to break */
/* If you do not use a third part user system, uncomment these constraints */
/* ALTER TABLE jiveUserProp ADD CONSTRAINT jiveUserProp_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser; */


CREATE TABLE jivePrivate (
  userID                INTEGER         NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  namespace             NVARCHAR(200)   NOT NULL,
  value                 NTEXT           NOT NULL,
  CONSTRAINT JivePrivate_pk PRIMARY KEY (userID, name, namespace)
);
/* Commented out since this will cause a third party user system to break */
/* If you do not use a third part user system, uncomment these constraints */
/* ALTER TABLE jivePrivate ADD CONSTRAINT jivePrivate_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser; */


CREATE TABLE jiveOffline (
  userID                INTEGER         NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               NVARCHAR(3700)  NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (userID, messageID)
);
/* Commented out since this will cause a third party user system to break */
/* If you do not use a third part user system, uncomment these constraints */
/* ALTER TABLE jiveOffline ADD CONSTRAINT jiveOffline_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser; */


CREATE TABLE jiveRoster (
  rosterID              INTEGER         NOT NULL,
  userID                INTEGER         NOT NULL,
  jid                   NVARCHAR(3000)  NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  NVARCHAR(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveRoster_userID_idx ON jiveRoster (userID ASC);
/* Commented out since this will cause a third party user system to break */
/* If you do not use a third part user system, uncomment these constraints */
/* ALTER TABLE jiveRoster ADD CONSTRAINT jiveRoster_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser; */


CREATE TABLE jiveRosterGroups (
  rosterID              INTEGER         NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             NVARCHAR(255)   NOT NULL,
  CONSTRAINT jiveRosterGroups_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRosterGroups_rosterid_idx ON jiveRosterGroups (rosterID ASC);
ALTER TABLE jiveRosterGroups ADD CONSTRAINT jiveRosterGroups_rosterID_fk FOREIGN KEY (rosterID) REFERENCES jiveRoster;


CREATE TABLE jiveVCard (
  userID                INTEGER         NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  propValue             NVARCHAR(3900)  NOT NULL,
  CONSTRAINT JiveVCard_pk PRIMARY KEY (userID, name)
);
/* Commented out since this will cause a third party user system to break */
/* If you do not use a third part user system, uncomment these constraints */
/* ALTER TABLE jiveVCard ADD CONSTRAINT jiveVCard_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser; */


CREATE TABLE jiveDomain (
  domainID              INTEGER         NOT NULL,
  name                  NVARCHAR(100)   UNIQUE NOT NULL,
  description           NVARCHAR(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveDomain_pk PRIMARY KEY (domainID)
);


CREATE TABLE jiveChatbot (
  chatbotID             INTEGER         NOT NULL,
  description           NVARCHAR(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveChatbot_pk PRIMARY KEY (chatbotID)
);


CREATE TABLE jiveUserID (
  username              NVARCHAR(30)    UNIQUE NOT NULL,
  domainID              INTEGER         NOT NULL,
  objectType            INTEGER         NOT NULL,
  objectID              INTEGER         NOT NULL,
  CONSTRAINT jiveUserID_pk PRIMARY KEY (username, domainID)
);
CREATE INDEX jiveUserID_object_idx on jiveUserID (objectType, objectID);


CREATE TABLE jiveGroup (
  groupID               INTEGER         NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  description           NVARCHAR(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT group_pk PRIMARY KEY (groupID)
);
CREATE INDEX jiveGroup_cDate_idx ON jiveGroup (creationDate);
CREATE INDEX jiveGroup_name_idx ON jiveGroup (name);


CREATE TABLE jiveGroupProp (
   groupID              INTEGER         NOT NULL,
   name                 NVARCHAR(100)   NOT NULL,
   propValue            NVARCHAR(3900)  NOT NULL,
   CONSTRAINT jiveGroupProp_pk PRIMARY KEY (groupID, name)
);
/* Commented out since this will cause a third party user system to break */
/* If you do not use a third part user system, uncomment these constraints */
/* ALTER TABLE jiveGroupProp ADD CONSTRAINT jiveGroupProp_groupID_fk FOREIGN KEY (groupID) REFERENCES jiveGroup; */


CREATE TABLE jiveGroupUser (
  groupID               INTEGER         NOT NULL,
  userID                INTEGER         NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser_pk PRIMARY KEY (groupID, userID, administrator)
);
/* Commented out since this will cause a third party user system to break */
/* If you do not use a third part user system, uncomment these constraints */
/* ALTER TABLE jiveGroupUser ADD CONSTRAINT jiveGroupUser_groupID_fk FOREIGN KEY (groupID) REFERENCES jiveGroup; */
/* ALTER TABLE jiveGroupUser ADD CONSTRAINT jiveGroupUser_userID_fk FOREIGN KEY (userID) REFERENCES jiveUser; */

 
CREATE TABLE jiveID (
  idType                INTEGER         NOT NULL,
  id                    INTEGER         NOT NULL,
  CONSTRAINT jiveID_pk PRIMARY KEY (idType)
);

CREATE TABLE jiveProperty (
  name         NVARCHAR(100) NOT NULL,
  propValue   NTEXT NOT NULL,
  CONSTRAINT jiveProperty_pk PRIMARY KEY (name)
);

/* MUC Tables */

CREATE TABLE mucRoom (
  roomID              INT           NOT NULL,
  name                NVARCHAR(50)  NOT NULL,
  description         NVARCHAR(255),
  canChangeSubject    INT           NOT NULL,
  maxUsers            INT           NOT NULL,
  publicRoom          INT           NOT NULL,
  moderated           INT           NOT NULL,
  invitationRequired  INT           NOT NULL,
  canInvite           INT           NOT NULL,
  password            NVARCHAR(50)  NULL,
  canDiscoverJID      INT           NOT NULL,
  logEnabled          INT           NOT NULL,
  subject             NVARCHAR(100) NULL,
  rolesToBroadcast    INT           NOT NULL,
  lastActiveDate      CHAR(15)      NULL,
  inMemory            INT           NOT NULL,
  CONSTRAINT mucRoom__pk PRIMARY KEY (name)
);

CREATE INDEX mucRoom_roomID_idx on mucRoom(roomID);

CREATE TABLE mucAffiliation (
  roomID              INT            NOT NULL,
  jid                 NVARCHAR(3000) NOT NULL,
  affiliation         INT            NOT NULL,
  CONSTRAINT mucAffiliation__pk PRIMARY KEY (roomID,jid)
);

CREATE TABLE mucMember (
  roomID              INT            NOT NULL,
  jid                 NVARCHAR(3000) NOT NULL,
  nickname            NVARCHAR(255)  NULL,
  CONSTRAINT mucMember__pk PRIMARY KEY (roomID,jid)
);

CREATE TABLE mucConversationLog (
  roomID              INT            NOT NULL,
  sender              NVARCHAR(3000) NOT NULL,
  nickname            NVARCHAR(255)  NULL,
  time                CHAR(15)       NOT NULL,
  subject             NVARCHAR(255)  NULL,
  body                NVARCHAR(3700) NULL
);

/* Finally, insert default table values. */

/* Unique ID entry for user, group */
/* The User ID entry starts at 2 (after admin user entry). */
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

/* Entry for admin user -- password is "admin" */
INSERT INTO jiveUserID (username, domainID, objectType, objectID) VALUES ('admin', 1, 0, 1);
INSERT INTO jiveUser (userID, name, password, email, emailVisible, nameVisible, creationDate, modificationDate)
    VALUES (1, 'Administrator', 'admin', 'admin@example.com', 1, 1, '0', '0');

/* Make the administrator an admin member of the Administrators group */
INSERT INTO jiveGroup (groupID, name, description, creationDate, modificationDate)
    VALUES (1, 'Administrators', 'Messenger Server administrators', '0', '0');
INSERT INTO jiveGroupUser (groupID, userID, administrator) VALUES (1, 1, 1); 