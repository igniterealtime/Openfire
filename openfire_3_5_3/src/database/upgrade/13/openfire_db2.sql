-- $Revision:  $
-- $Date:  $

-- Rename column
CREATE TABLE jiveRemoteServerConf2 (
  xmppDomain            VARCHAR(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR(10)     NOT NULL,
);

INSERT INTO jiveRemoteServerConf2 (xmppDomain, remotePort, permission)
SELECT domain, remotePort, permission FROM jiveRemoteServerConf;

DROP TABLE jiveRemoteServerConf;
RENAME TABLE jiveRemoteServerConf2 TO jiveRemoteServerConf;
ALTER TABLE jiveRemoteServerConf ADD CONSTRAINT jiveRmSrvConf_pk PRIMARY KEY xmppDomain;

-- Rename column
CREATE TABLE jiveOffline2 (
  username              VARCHAR(64)     NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  stanza                VARCHAR(2000)   NOT NULL,
);

INSERT INTO jiveOffline2 (username, messageID, creationDate, messageSize, stanza)
SELECT username, messageID, creationDate, messageSize, message FROM jiveOffline;

DROP TABLE jiveOffline;
RENAME TABLE jiveOffline2 TO jiveOffline;
ALTER TABLE jiveOffline ADD CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID);

-- Rename column
CREATE TABLE jiveVCard2 (
  username              VARCHAR(64)     NOT NULL,
  vcard                 VARCHAR(2000)   NOT NULL,
);

INSERT INTO jiveVCard2 (username, vcard)
SELECT username, value FROM jiveVCard;

DROP TABLE jiveVCard;
RENAME TABLE jiveVCard2 TO jiveVCard;
ALTER TABLE jiveVCard ADD CONSTRAINT jiveVCard_pk PRIMARY KEY (username);

-- Rename column
CREATE TABLE jivePrivate2 (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(200)    NOT NULL,
  privateData           VARCHAR(2000)   NOT NULL,
);

-- Rename column
CREATE TABLE jiveVCard2 (
  username              VARCHAR(64)     NOT NULL,
  vcard                 VARCHAR(2000)   NOT NULL,
);

INSERT INTO jiveVCard2 (username, vcard)
SELECT username, value FROM jiveVCard;

DROP TABLE jiveVCard;
RENAME TABLE jiveVCard2 TO jiveVCard;
ALTER TABLE jiveVCard ADD CONSTRAINT jiveVCard_pk PRIMARY KEY (username);

-- Rename column
CREATE TABLE jiveUser2 (
  username              VARCHAR(64)     NOT NULL,
  plainPassword         VARCHAR(32),
  encryptedPassword     VARCHAR(255),
  name                  VARCHAR(100),
  email                 VARCHAR(100),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
);

INSERT INTO jiveUser2 (username, plainPassword, encryptedPassword, name, email, creationDate, modificationDate)
SELECT username, password, encryptedPassword, name, email, creationDate, modificationDate FROM jiveUser;

DROP TABLE jiveUser;
RENAME TABLE jiveUser2 TO jiveUser;
ALTER TABLE jiveUser ADD CONSTRAINT jiveUser_pk PRIMARY KEY (username);
CREATE INDEX jiveUsr_cDate_idx ON jiveUser (creationDate ASC);

-- Rename column
CREATE TABLE mucRoom2 (
  roomID              INTEGER       NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  naturalName         VARCHAR(255)  NOT NULL,
  description         VARCHAR(255),
  lockedDate          CHAR(15)      NOT NULL,
  emptyDate           CHAR(15),
  canChangeSubject    INTEGER       NOT NULL,
  maxUsers            INTEGER       NOT NULL,
  publicRoom          INTEGER       NOT NULL,
  moderated           INTEGER       NOT NULL,
  membersOnly         INTEGER       NOT NULL,
  canInvite           INTEGER       NOT NULL,
  roomPassword        VARCHAR(50),
  canDiscoverJID      INTEGER       NOT NULL,
  logEnabled          INTEGER       NOT NULL,
  subject             VARCHAR(100),
  rolesToBroadcast    INTEGER       NOT NULL,
  useReservedNick     INTEGER       NOT NULL,
  canChangeNick       INTEGER       NOT NULL,
  canRegister         INTEGER       NOT NULL,
);

INSERT INTO mucRoom2 (roomID, creationDate, modificationDate, name, naturalName, description, lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, canInvite, roomPassword, canDiscoverJID, logEnabled, subject, rolesToBroadcast, useReservedNick, canChangeNick, canRegister)
SELECT roomID, creationDate, modificationDate, name, naturalName, description, lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, canInvite, password, canDiscoverJID, logEnabled, subject, rolesToBroadcast, useReservedNick, canChangeNick, canRegister FROM mucRoom;

DROP TABLE mucRoom;
RENAME TABLE mucRoom2 TO mucRoom;
ALTER TABLE mucRoom ADD CONSTRAINT mucRoom_pk PRIMARY KEY (name);
CREATE INDEX mucRm_roomid_idx ON mucRoom (roomID);

UPDATE jiveVersion set version=13 where name = 'openfire';


