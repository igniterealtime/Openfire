/*
 * $RCSfile$
 * $Revision$
 * $Date$
 */

CREATE TABLE jiveUser (
  username              NVARCHAR(32)    NOT NULL,
  password              NVARCHAR(32)    NOT NULL,
  name                  NVARCHAR(100),
  email                 VARCHAR(100),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveUser_pk PRIMARY KEY (username)
);
CREATE INDEX jiveUser_cDate_idx ON jiveUser (creationDate ASC);


CREATE TABLE jiveUserProp (
  username              NVARCHAR(32)    NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  propValue             NVARCHAR(2000)  NOT NULL,
  CONSTRAINT jiveUserProp_pk PRIMARY KEY (username, name)
);


CREATE TABLE jivePrivate (
  username              NVARCHAR(32)    NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  namespace             NVARCHAR(200)   NOT NULL,
  value                 NTEXT           NOT NULL,
  CONSTRAINT JivePrivate_pk PRIMARY KEY (username, name, namespace)
);


CREATE TABLE jiveOffline (
  username              NVARCHAR(32)    NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               NTEXT           NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID)
);


CREATE TABLE jiveRoster (
  rosterID              INTEGER         NOT NULL,
  username              NVARCHAR(32)    NOT NULL,
  jid                   NVARCHAR(1024)  NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  NVARCHAR(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveRoster_username_idx ON jiveRoster (username ASC);


CREATE TABLE jiveRosterGroups (
  rosterID              INTEGER         NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             NVARCHAR(255)   NOT NULL,
  CONSTRAINT jiveRosterGroups_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRosterGroups_rosterid_idx ON jiveRosterGroups (rosterID ASC);
ALTER TABLE jiveRosterGroups ADD CONSTRAINT jiveRosterGroups_rosterID_fk FOREIGN KEY (rosterID) REFERENCES jiveRoster;


CREATE TABLE jiveVCard (
  username              NVARCHAR(32)    NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  propValue             NVARCHAR(2000)  NOT NULL,
  CONSTRAINT JiveVCard_pk PRIMARY KEY (username, name)
);


CREATE TABLE jiveGroup (
  name                  NVARCHAR(100)   NOT NULL,
  description           NVARCHAR(255),
  CONSTRAINT group_pk PRIMARY KEY (name)
);


CREATE TABLE jiveGroupProp (
   groupName            NVARCHAR(100)   NOT NULL,
   name                 NVARCHAR(100)   NOT NULL,
   propValue            NVARCHAR(2000)  NOT NULL,
   CONSTRAINT jiveGroupProp_pk PRIMARY KEY (groupName, name)
);


CREATE TABLE jiveGroupUser (
  groupName             NVARCHAR(100)   NOT NULL,
  username              NVARCHAR(32)    NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser_pk PRIMARY KEY (groupName, username, administrator)
);


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
  CONSTRAINT mucRoom__pk PRIMARY KEY (name)
);

CREATE INDEX mucRoom_roomID_idx on mucRoom(roomID);

CREATE TABLE mucAffiliation (
  roomID              INT            NOT NULL,
  jid                 NVARCHAR(1024) NOT NULL,
  affiliation         INT            NOT NULL,
  CONSTRAINT mucAffiliation__pk PRIMARY KEY (roomID,jid)
);

CREATE TABLE mucMember (
  roomID              INT            NOT NULL,
  jid                 NVARCHAR(1024) NOT NULL,
  nickname            NVARCHAR(255)  NULL,
  CONSTRAINT mucMember__pk PRIMARY KEY (roomID,jid)
);

CREATE TABLE mucConversationLog (
  roomID              INT            NOT NULL,
  sender              NVARCHAR(1024) NOT NULL,
  nickname            NVARCHAR(255)  NULL,
  time                CHAR(15)       NOT NULL,
  subject             NVARCHAR(255)  NULL,
  body                NTEXT          NULL
);
CREATE INDEX mucLog_time_idx ON mucConversationLog (time, roomID);

/* Finally, insert default table values. */

INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
INSERT INTO jiveID (idType, id) VALUES (23, 1);

/* Entry for admin user */
INSERT INTO jiveUser (username, password, name, email, creationDate, modificationDate)
    VALUES ('admin', 'admin', 'Administrator', 'admin@example.com', '0', '0');