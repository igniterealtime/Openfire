--   $RCSfile$   
--   $Revision$
--   $Date$

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
CREATE INDEX jiveUsr_cDate_idx ON jiveUser (creationDate ASC);


CREATE TABLE jiveUserProp (
  userID                INTEGER         NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(2000)   NOT NULL,
  CONSTRAINT jiveUsrProp_pk PRIMARY KEY (userID, name)
);


CREATE TABLE jivePrivate (
  userID                INTEGER         NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(200)    NOT NULL,
  value                 LONG VARCHAR    NOT NULL,
  CONSTRAINT JivePrivate_pk PRIMARY KEY (userID, name, namespace)
);


CREATE TABLE jiveOffline (
  userID                INTEGER         NOT NULL,
  messageID             INTEGER         NOT NULL,
  creationDate          CHAR(15)        NOT NULL,
  messageSize           INTEGER         NOT NULL,
  message               LONG VARCHAR    NOT NULL,
  CONSTRAINT jiveOffline_pk PRIMARY KEY (userID, messageID)
);


CREATE TABLE jiveRoster (
  rosterID              INTEGER         NOT NULL,
  userID                INTEGER         NOT NULL,
  jid                   VARCHAR(2000)   NOT NULL,
  sub                   INTEGER         NOT NULL,
  ask                   INTEGER         NOT NULL,
  recv                  INTEGER         NOT NULL,
  nick                  VARCHAR(255),
  CONSTRAINT jiveRoster_pk PRIMARY KEY (rosterID)
);
CREATE INDEX jiveR_userid_idx ON jiveRoster (userID ASC);


CREATE TABLE jiveRosterGroups (
  rosterID              INTEGER         NOT NULL,
  rank                  INTEGER         NOT NULL,
  groupName             VARCHAR(255)    NOT NULL,
  CONSTRAINT jiveRoGrps_pk PRIMARY KEY (rosterID, rank)
);
CREATE INDEX jiveRoGrps_rid_idx ON jiveRosterGroups (rosterID ASC);


CREATE TABLE jiveVCard (
  userID                INTEGER         NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(2000)   NOT NULL,
  CONSTRAINT JiveVCard_pk PRIMARY KEY (userID, name)
);


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
  username              VARCHAR(30)     UNIQUE NOT NULL,
  domainID              INTEGER         NOT NULL,
  objectType            INTEGER         NOT NULL,
  objectID              INTEGER         NOT NULL,
  CONSTRAINT jiveUserID_pk PRIMARY KEY (username, domainID)
);
CREATE INDEX jiveUsrID_obj_idx ON jiveUserID (objectType, objectID);


CREATE TABLE jiveGroup (
  groupID               INTEGER         NOT NULL,
  name                  VARCHAR(50)     NOT NULL,
  description           VARCHAR(255),
  creationDate          CHAR(15)        NOT NULL,
  modificationDate      CHAR(15)        NOT NULL,
  CONSTRAINT jiveGroup_pk PRIMARY KEY (groupID)
);
CREATE INDEX jiveGrp_cDate_idx ON jiveGroup (creationDate ASC);
CREATE INDEX jiveGrp_name_idx ON jiveGroup (name);


CREATE TABLE jiveGroupProp (
  groupID               INTEGER         NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(2000)   NOT NULL,
  CONSTRAINT jiveGrpProp_pk PRIMARY KEY (groupID, name)
);


CREATE TABLE jiveGroupUser (
  groupID               INTEGER         NOT NULL,
  userID                INTEGER         NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGrpUser PRIMARY KEY (groupID, userID, administrator)
);

 
CREATE TABLE jiveID (
  idType                INTEGER         NOT NULL,
  id                    INTEGER         NOT NULL,
  CONSTRAINT jiveID_pk PRIMARY KEY (idType)
);


CREATE TABLE jiveProperty (
  name        VARCHAR(100) NOT NULL,
  propValue   VARCHAR(3000) NOT NULL,
  CONSTRAINT jiveProperty_pk PRIMARY KEY (name)
);

-- MUC tables

CREATE TABLE mucRoom (
  roomID              INTEGER       NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  naturalRoomName     VARCHAR(255)  NOT NULL,
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
  CONSTRAINT mucRoom_pk PRIMARY KEY (name)
);

CREATE INDEX mucRoom_roomid_idx ON mucRoom (roomID);

CREATE TABLE mucAffiliation (
  roomID              INTEGER       NOT NULL,
  jid                 VARCHAR(2000) NOT NULL,
  affiliation         INTEGER       NOT NULL,
  CONSTRAINT mucAffiliation_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucMember (
  roomID              INTEGER       NOT NULL,
  jid                 VARCHAR(2000) NOT NULL,
  nickname            VARCHAR(255)  NULL,
  CONSTRAINT mucMember_pk PRIMARY KEY (roomID, jid)
);

CREATE TABLE mucConversationLog (
  roomID              INTEGER       NOT NULL,
  sender              VARCHAR(2000) NOT NULL,
  nickname            VARCHAR(255)  NULL,
  time                CHAR(15)      NOT NULL,
  subject             VARCHAR(255)  NULL,
  body                LONG VARCHAR  NULL
);


-- Finally, insert default table values

INSERT INTO jiveID (idType, id) VALUES (0, 1);
INSERT INTO jiveID (idType, id) VALUES (1, 1);
INSERT INTO jiveID (idType, id) VALUES (2, 1);
INSERT INTO jiveID (idType, id) VALUES (3, 2);
INSERT INTO jiveID (idType, id) VALUES (4, 1);
INSERT INTO jiveID (idType, id) VALUES (13, 1);
INSERT INTO jiveID (idType, id) VALUES (14, 2);
INSERT INTO jiveID (idType, id) VALUES (18, 1);
INSERT INTO jiveID (idType, id) VALUES (19, 1);
insert INTO jiveID (idType, id) VALUES (23, 1);

-- Entry for admin user -- password is "admin"
INSERT INTO jiveUserID (username, domainID, objectType, objectID) VALUES ('admin', 1, 0, 1);
INSERT INTO jiveUser (userID, name, password, email, emailVisible, nameVisible, creationDate, modificationDate)
    VALUES (1, 'Administrator', 'admin', 'admin@example.com', 1, 1, '0', '0');

-- Make the administrator an admin member of the Administrators group
INSERT INTO jiveGroup (groupID, name, description, creationDate, modificationDate)
    VALUES (1, 'Administrators', 'Messenger Server administrators', '0', '0');
INSERT INTO jiveGroupUser (groupID, userID, administrator) VALUES (1, 1, 1);
