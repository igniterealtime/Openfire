/*
 * $RCSfile$
 * $Revision$
 * $Date$
 */

/* upgrades from Messenger 1.1.x to 2.0.x */

/* jiveUser: Adds new column "username". Removes "nameVisible" & "emailVisible". Changes primary key */
ALTER TABLE jiveUser ADD COLUMN username NVARCHAR(32) NOT NULL;
ALTER TABLE jiveUser DROP COLUMN nameVisible;
ALTER TABLE jiveUser DROP COLUMN emailVisible;
UPDATE jiveUser,jiveUserID SET jiveUser.username = jiveUserID.username where jiveUserID.objectID = jiveUser.userID;
ALTER TABLE jiveUser DROP PRIMARY KEY;
ALTER TABLE jiveUser ADD CONSTRAINT jiveUser_pk PRIMARY KEY (username);

/* jiveUserProp: Adds new column "username". Changes primary key */
ALTER TABLE jiveUserProp ADD COLUMN username NVARCHAR(32) NOT NULL;
UPDATE jiveUserProp,jiveUser SET jiveUserProp.username = jiveUser.username where jiveUserProp.userID = jiveUser.userID;
ALTER TABLE jiveUserProp DROP PRIMARY KEY;
ALTER TABLE jiveUserProp ADD CONSTRAINT jiveUserProp_pk PRIMARY KEY (username, name);

/* jiveGroupUser: Adds new column "username". Changes primary key */
ALTER TABLE jiveGroupUser ADD COLUMN username NVARCHAR(32) NOT NULL;
UPDATE jiveGroupUser,jiveUser SET jiveGroupUser.username = jiveUser.username where jiveGroupUser.userID = jiveUser.userID;
ALTER TABLE jiveGroupUser DROP PRIMARY KEY;
ALTER TABLE jiveGroupUser ADD CONSTRAINT jiveGroupUser_pk PRIMARY KEY (groupID, username, administrator);

/* jivePrivate: Adds new column "username". Changes primary key */
ALTER TABLE jivePrivate ADD COLUMN username NVARCHAR(32) NOT NULL;
UPDATE jivePrivate,jiveUser SET jivePrivate.username = jiveUser.username where jivePrivate.userID = jiveUser.userID;
ALTER TABLE jivePrivate DROP PRIMARY KEY;
ALTER TABLE jivePrivate ADD CONSTRAINT jivePrivate_pk PRIMARY KEY (username, name, namespace);

/* jiveOffline: Adds new column "username". Changes primary key */
ALTER TABLE jiveOffline ADD COLUMN username NVARCHAR(32) NOT NULL;
UPDATE jiveOffline ,jiveUser SET jiveOffline.username = jiveUser.username where jiveOffline.userID = jiveUser.userID;
ALTER TABLE jiveOffline  DROP PRIMARY KEY;
ALTER TABLE jiveOffline  ADD CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID);

/* jiveRoster: Adds new column "username". Replaces old index with new one */
ALTER TABLE jiveRoster ADD COLUMN username NVARCHAR(32) NOT NULL;
UPDATE jiveRoster,jiveUser SET jiveRoster.username = jiveUser.username where jiveRoster.userID = jiveUser.userID;
DROP INDEX jiveRoster_userID_idx;
CREATE INDEX jiveRoster_username_idx ON jiveRoster (username ASC);

/* jiveVCard: Adds new column "username". Changes primary key */
ALTER TABLE jiveVCard ADD COLUMN username NVARCHAR(32) NOT NULL;
UPDATE jiveVCard ,jiveUser SET jiveVCard.username = jiveUser.username where jiveVCard.userID = jiveUser.userID;
ALTER TABLE jiveVCard  DROP PRIMARY KEY;
ALTER TABLE jiveVCard  ADD CONSTRAINT jiveVCard_pk PRIMARY KEY (username, name);

/* Drops no longer needed tables */
DROP TABLE jiveUserID;
DROP TABLE jiveChatbot;
DROP TABLE jiveDomain;
DROP TABLE jiveUserPerm;

/* Deletes no longer needed entries */
DELETE FROM jiveID where idType = 0;
DELETE FROM jiveID where idType = 1;
DELETE FROM jiveID where idType = 2;
DELETE FROM jiveID where idType = 13;
DELETE FROM jiveID where idType = 14;

/* Finally remove "userID" column */
ALTER TABLE jiveUserProp DROP COLUMN userID;
ALTER TABLE jiveUser DROP COLUMN userID;
ALTER TABLE jiveGroupUser DROP COLUMN userID;
ALTER TABLE jivePrivate DROP COLUMN userID;
ALTER TABLE jiveOffline DROP COLUMN userID;
ALTER TABLE jiveRoster DROP COLUMN userID;
ALTER TABLE jiveVCard DROP COLUMN userID;

/* Create new tables */

CREATE TABLE jiveProperty (
  name         NVARCHAR(100) NOT NULL,
  propValue   NTEXT NOT NULL,
  CONSTRAINT jiveProperty_pk PRIMARY KEY (name)
);

/* MUC Tables */

CREATE TABLE mucRoom (
  roomID              INT           NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                NVARCHAR(50)  NOT NULL,
  naturalName         NVARCHAR(255) NOT NULL,
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

/* Unique ID entry for mucRoom */
INSERT INTO jiveID (idType, id) VALUES (23, 1);