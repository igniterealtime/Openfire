REM // $RCSfile$
REM // $Revision$
REM // $Date$

REM // upgrades from Messenger 1.1.x to 2.0.x

REM // jiveUser: Adds new column "username". Removes "nameVisible" & "emailVisible". Changes primary key
ALTER TABLE jiveUser ADD username VARCHAR2(32) NOT NULL;
ALTER TABLE jiveUser DROP COLUMN nameVisible;
ALTER TABLE jiveUser DROP COLUMN emailVisible;
UPDATE jiveUser,jiveUserID SET jiveUser.username = jiveUserID.username where jiveUserID.objectID = jiveUser.userID;
ALTER TABLE jiveUser DROP PRIMARY KEY;
ALTER TABLE jiveUser ADD CONSTRAINT jiveUser_pk PRIMARY KEY (username);

REM // jiveUserProp: Adds new column "username". Changes primary key
ALTER TABLE jiveUserProp ADD username VARCHAR2(32) NOT NULL;
UPDATE jiveUserProp,jiveUser SET jiveUserProp.username = jiveUser.username where jiveUserProp.userID = jiveUser.userID;
ALTER TABLE jiveUserProp DROP PRIMARY KEY;
ALTER TABLE jiveUserProp ADD CONSTRAINT jiveUserProp_pk PRIMARY KEY (username, name);

REM // jiveGroupUser: Adds new column "username". Changes primary key
ALTER TABLE jiveGroupUser ADD username VARCHAR2(32) NOT NULL;
UPDATE jiveGroupUser,jiveUser SET jiveGroupUser.username = jiveUser.username where jiveGroupUser.userID = jiveUser.userID;
ALTER TABLE jiveGroupUser DROP PRIMARY KEY;
ALTER TABLE jiveGroupUser ADD CONSTRAINT jiveGroupUser PRIMARY KEY (groupID, username, administrator);

REM // jivePrivate: Adds new column "username". Changes primary key
ALTER TABLE jivePrivate ADD username VARCHAR2(32) NOT NULL;
UPDATE jivePrivate,jiveUser SET jivePrivate.username = jiveUser.username where jivePrivate.userID = jiveUser.userID;
ALTER TABLE jivePrivate DROP PRIMARY KEY;
ALTER TABLE jivePrivate ADD CONSTRAINT jivePrivate_pk PRIMARY KEY (username, name, namespace);

REM // jiveOffline: Adds new column "username". Changes primary key
ALTER TABLE jiveOffline ADD username VARCHAR2(32) NOT NULL;
UPDATE jiveOffline ,jiveUser SET jiveOffline.username = jiveUser.username where jiveOffline.userID = jiveUser.userID;
ALTER TABLE jiveOffline DROP PRIMARY KEY;
ALTER TABLE jiveOffline ADD CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID);

REM // jiveRoster: Adds new column "username". Replaces old index with new one
ALTER TABLE jiveRoster ADD username VARCHAR2(32) NOT NULL;
UPDATE jiveRoster,jiveUser SET jiveRoster.username = jiveUser.username where jiveRoster.userID = jiveUser.userID;
DROP INDEX jiveRoster_userid_idx;
CREATE INDEX jiveRoster_username_idx ON jiveRoster (username ASC);

REM // jiveVCard: Adds new column "username". Changes primary key
ALTER TABLE jiveVCard ADD username VARCHAR2(32) NOT NULL;
UPDATE jiveVCard ,jiveUser SET jiveVCard.username = jiveUser.username where jiveVCard.userID = jiveUser.userID;
ALTER TABLE jiveVCard DROP PRIMARY KEY;
ALTER TABLE jiveVCard ADD CONSTRAINT jiveVCard_pk PRIMARY KEY (username, name);

REM // Drops no longer needed tables
DROP TABLE jiveUserID;
DROP TABLE jiveChatbot;
DROP TABLE jiveDomain;
DROP TABLE jiveUserPerm;

REM // Deletes no longer needed entries
DELETE FROM jiveID where idType = 0;
DELETE FROM jiveID where idType = 1;
DELETE FROM jiveID where idType = 2;
DELETE FROM jiveID where idType = 13;
DELETE FROM jiveID where idType = 14;

REM // Finally remove "userID" column
ALTER TABLE jiveUserProp DROP COLUMN userID;
ALTER TABLE jiveUser DROP COLUMN userID;
ALTER TABLE jiveGroupUser DROP COLUMN userID;
ALTER TABLE jivePrivate DROP COLUMN userID;
ALTER TABLE jiveOffline DROP COLUMN userID;
ALTER TABLE jiveRoster DROP COLUMN userID;
ALTER TABLE jiveVCard DROP COLUMN userID;

REM // Create new tables

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

REM // Unique ID entry for mucRoom
INSERT INTO jiveID (idType, id) VALUES (23, 1);