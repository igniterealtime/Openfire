# $Revision: 395 $
# $Date: 2004-11-13 16:57:13 -0300 (Sat, 13 Nov 2004) $

# jiveUser: Adds new column "username". Removes "nameVisible" & "emailVisible". Changes primary key
ALTER TABLE jiveUser ADD COLUMN username VARCHAR(32) NOT NULL;
ALTER TABLE jiveUser DROP COLUMN nameVisible;
ALTER TABLE jiveUser DROP COLUMN emailVisible;
UPDATE jiveUser,jiveUserID SET jiveUser.username = jiveUserID.username where jiveUserID.objectID = jiveUser.userID;
ALTER TABLE jiveUser DROP PRIMARY KEY;
ALTER TABLE jiveUser ADD PRIMARY KEY (username);

# jiveUserProp: Adds new column "username". Changes primary key
ALTER TABLE jiveUserProp ADD COLUMN username VARCHAR(32) NOT NULL;
UPDATE jiveUserProp,jiveUser SET jiveUserProp.username = jiveUser.username where jiveUserProp.userID = jiveUser.userID;
ALTER TABLE jiveUserProp DROP PRIMARY KEY;
ALTER TABLE jiveUserProp ADD PRIMARY KEY (username, name);

# jiveGroupUser: Adds new column "username". Changes primary key
ALTER TABLE jiveGroupUser ADD COLUMN username VARCHAR(32) NOT NULL;
UPDATE jiveGroupUser,jiveUser SET jiveGroupUser.username = jiveUser.username where jiveGroupUser.userID = jiveUser.userID;
ALTER TABLE jiveGroupUser DROP PRIMARY KEY;
ALTER TABLE jiveGroupUser ADD PRIMARY KEY (groupID, username, administrator);

# jivePrivate: Adds new column "username". Changes primary key
ALTER TABLE jivePrivate ADD COLUMN username VARCHAR(32) NOT NULL;
UPDATE jivePrivate,jiveUser SET jivePrivate.username = jiveUser.username where jivePrivate.userID = jiveUser.userID;
ALTER TABLE jivePrivate DROP PRIMARY KEY;
ALTER TABLE jivePrivate ADD PRIMARY KEY (username, name, namespace);

# jiveOffline: Adds new column "username". Changes primary key
ALTER TABLE jiveOffline ADD COLUMN username VARCHAR(32) NOT NULL;
UPDATE jiveOffline ,jiveUser SET jiveOffline.username = jiveUser.username where jiveOffline.userID = jiveUser.userID;
ALTER TABLE jiveOffline  DROP PRIMARY KEY;
ALTER TABLE jiveOffline  ADD PRIMARY KEY (username, messageID);

# jiveRoster: Adds new column "username". Replaces old index with new one
ALTER TABLE jiveRoster ADD COLUMN username VARCHAR(32) NOT NULL;
UPDATE jiveRoster,jiveUser SET jiveRoster.username = jiveUser.username where jiveRoster.userID = jiveUser.userID;
ALTER TABLE jiveRoster DROP INDEX jiveRoster_userid_idx;
ALTER TABLE jiveRoster ADD INDEX jiveRoster_unameid_idx (username);

# jiveVCard: Adds new column "username". Changes primary key
ALTER TABLE jiveVCard ADD COLUMN username VARCHAR(32) NOT NULL;
UPDATE jiveVCard ,jiveUser SET jiveVCard.username = jiveUser.username where jiveVCard.userID = jiveUser.userID;
ALTER TABLE jiveVCard  DROP PRIMARY KEY;
ALTER TABLE jiveVCard  ADD PRIMARY KEY (username, name);

# Drops no longer needed tables
DROP TABLE jiveUserID;
DROP TABLE jiveChatbot;
DROP TABLE jiveDomain;
DROP TABLE jiveUserPerm;

# Deletes no longer needed entries
DELETE FROM jiveID where idType = 0;
DELETE FROM jiveID where idType = 1;
DELETE FROM jiveID where idType = 2;
DELETE FROM jiveID where idType = 13;
DELETE FROM jiveID where idType = 14;

# Finally remove "userID" column
ALTER TABLE jiveUserProp DROP COLUMN userID;
ALTER TABLE jiveUser DROP COLUMN userID;
ALTER TABLE jiveGroupUser DROP COLUMN userID;
ALTER TABLE jivePrivate DROP COLUMN userID;
ALTER TABLE jiveOffline DROP COLUMN userID;
ALTER TABLE jiveRoster DROP COLUMN userID;
ALTER TABLE jiveVCard DROP COLUMN userID;

# Create new tables

CREATE TABLE jiveProperty (
  name        VARCHAR(100) NOT NULL,
  propValue   TEXT NOT NULL,
  PRIMARY KEY (name)
);

# MUC Tables

CREATE TABLE mucRoom (
  roomID              BIGINT        NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  naturalName         VARCHAR(255)  NOT NULL,
  description         VARCHAR(255),
  canChangeSubject    TINYINT       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          TINYINT       NOT NULL,
  moderated           TINYINT       NOT NULL,
  invitationRequired  TINYINT       NOT NULL,
  canInvite           TINYINT       NOT NULL,
  password            VARCHAR(50)   NULL,
  canDiscoverJID      TINYINT       NOT NULL,
  logEnabled          TINYINT       NOT NULL,
  subject             VARCHAR(100)  NULL,
  rolesToBroadcast    TINYINT       NOT NULL,
  lastActiveDate      CHAR(15)      NULL,
  inMemory            TINYINT       NOT NULL,
  PRIMARY KEY (name),
  INDEX mucRoom_roomid_idx (roomID)
);

CREATE TABLE mucAffiliation (
  roomID              BIGINT        NOT NULL,
  jid                 TEXT          NOT NULL,
  affiliation         TINYINT       NOT NULL,
  PRIMARY KEY (roomID,jid(70))
);

CREATE TABLE mucMember (
  roomID              BIGINT        NOT NULL,
  jid                 TEXT          NOT NULL,
  nickname            VARCHAR(255)  NULL,
  PRIMARY KEY (roomID,jid(70))
);

CREATE TABLE mucConversationLog (
  roomID              BIGINT        NOT NULL,
  sender              TEXT          NOT NULL,
  nickname            VARCHAR(255)  NULL,
  time                CHAR(15)      NOT NULL,
  subject             VARCHAR(255)  NULL,
  body                TEXT          NULL
);

# Unique ID entry for mucRoom
INSERT INTO jiveID (idType, id) VALUES (23, 1);