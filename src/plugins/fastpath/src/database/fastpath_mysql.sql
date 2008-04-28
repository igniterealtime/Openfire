# $Revision$
# $Date$

INSERT INTO ofVersion (name, version) VALUES ('fastpath', 0);

CREATE TABLE fpWorkgroup(
  workgroupID         BIGINT NOT NULL,
  jid                 VARCHAR(255) NOT NULL,
  displayName         VARCHAR(50) NULL,
  description         VARCHAR(255) NULL,
  status              INTEGER NOT NULL,
  modes               INTEGER NOT NULL,
  creationDate        VARCHAR(15) NOT NULL,
  modificationDate    VARCHAR(15) NOT NULL,
  maxchats            INTEGER NOT NULL,
  minchats            INTEGER NOT NULL,
  requestTimeout      INTEGER NOT NULL,
  offerTimeout        INTEGER NOT NULL,
  schedule            TEXT NULL,
  PRIMARY KEY (workgroupID),
  INDEX fpWorkgroup_workgroupid_idx(workgroupID)
);

CREATE TABLE fpWorkgroupProp (
  ownerID       BIGINT       NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     MEDIUMTEXT   NOT NULL,
  PRIMARY KEY   (ownerID,name)
);

CREATE TABLE fpAgent(
  agentID             BIGINT NOT NULL,
  agentJID            VARCHAR(255) NOT NULL,
  name                VARCHAR(255) NULL,
  maxchats            INTEGER NOT NULL,
  minchats            INTEGER NOT NULL,
  PRIMARY KEY (agentJID),
  INDEX fpagent_agentid_idx(agentID),
  INDEX fpagent_agentjid_idx(agentJID)
);

CREATE TABLE fpAgentProp (
  ownerID       BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT NOT NULL,
  PRIMARY KEY   (ownerID,name)
);

CREATE TABLE fpQueue(
  queueID             BIGINT NOT NULL,
  workgroupID         BIGINT NOT NULL,
  name                VARCHAR(50) NOT NULL,
  description         VARCHAR(255) NULL,
  priority            INTEGER NOT NULL,
  maxchats            INTEGER NOT NULL,
  minchats            INTEGER NOT NULL,
  overflow            INTEGER NOT NULL,
  backupQueue         BIGINT NULL,
  PRIMARY KEY (workgroupID,queueID),
  INDEX fpqueue_workgroupid_idx(workgroupID),
  INDEX fpqueue_queueid_idx(queueID)
);

CREATE TABLE fpDispatcherProp (
  ownerID       BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT NOT NULL,
  PRIMARY KEY   (ownerID,name)
);


CREATE TABLE fpDispatcher(
  queueID             BIGINT NOT NULL,
  name                VARCHAR(50) NULL,
  description         VARCHAR(255) NULL,
  offerTimeout        INTEGER NOT NULL,
  requestTimeout      INTEGER NOT NULL,
  PRIMARY KEY (queueID)
);

CREATE TABLE fpQueueProp (
  ownerID       BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT NOT NULL,
  PRIMARY KEY   (ownerID,name)
);


CREATE TABLE fpQueueAgent (
  queueID        BIGINT NOT NULL,
  objectID       BIGINT NOT NULL,
  objectType     INTEGER NOT NULL,
  administrator  INTEGER NULL,
  PRIMARY KEY   (queueID,objectID,objectType)
);

CREATE TABLE fpQueueGroup (
  queueID  BIGINT NOT NULL,
  groupName    VARCHAR(50) NOT NULL,
  PRIMARY KEY (queueID,groupName)
);

CREATE TABLE fpSession(
  sessionID      varchar(31) NOT NULL,
  userID         varchar(255) NOT NULL,
  workgroupID    BIGINT NOT NULL,
  transcript     TEXT,
  startTime      CHAR(15) NOT NULL,
  endTime        CHAR(15) NOT NULL,
  queueWaitTime  BIGINT,
  state          int NOT NULL,
  caseID         varchar(20),
  status         CHAR(15),
  notes          TEXT,
  PRIMARY KEY (sessionID),
  INDEX fpsession_workgroupid_idx(workgroupID, userID),
  INDEX fpsession_starttime_idx(workgroupID, startTime)
);

CREATE TABLE fpAgentSession(
  sessionID varchar(31) NOT NULL,
  agentJID varchar(255) NOT NULL,
  joinTime CHAR(15) NOT NULL,
  leftTime CHAR(15),
  INDEX fpagentsession_sessionid_idx(sessionID)
);

CREATE TABLE fpSessionMetadata(
  sessionID varchar(31) NOT NULL,
  metadataName varChar(200) NOT NULL,
  metadataValue TEXT NOT NULL
);

CREATE TABLE fpSessionProp(
  sessionID     varchar(31)  NOT NULL,
  name          varchar(100) NOT NULL,
  propValue     TEXT         NOT NULL,
  PRIMARY KEY   (sessionID,name)
);

CREATE TABLE fpSetting (
  workgroupName         VARCHAR(100)    NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(255)    NOT NULL,
  value                 TEXT            NOT NULL,
  PRIMARY KEY (workgroupName, name)
);

CREATE TABLE fpSearchIndex (
  workgroupID         BIGINT   NOT NULL,
  lastUpdated         CHAR(15) NOT NULL,
  lastOptimization    CHAR(15) NOT NULL,
  PRIMARY KEY (workgroupID)
);

CREATE TABLE fpWorkgroupRoster (
  workgroupID         BIGINT NOT NULL,
  jid                 TEXT NOT NULL,
  lastContact         VARCHAR(15) NULL,
  PRIMARY KEY (workgroupID, jid(255)),
  INDEX fpWrkgrpRoster_workgroupjid_idx(workgroupID)
);

CREATE TABLE fpChatSetting(
  workgroupNode     varchar(100),
  type              int,
  label             varchar(100),
  description       varchar(255),
  name              varchar(100),
  value             text,
  defaultValue      text,
  INDEX fpChatSetting_idx(workgroupNode, name)
);

CREATE TABLE fpOfflineSetting (
  workgroupID BIGINT NOT NULL,
  redirectPage varChar(255),
  emailAddress varChar(255),
  subject varChar(255),
  offlineText TEXT,
  PRIMARY KEY(workgroupID)
);

CREATE TABLE fpRouteRule (
    workgroupID   BIGINT NOT NULL,
    queueID       BIGINT NOT NULL,
    rulePosition  INT NOT NULL,
    query         TEXT
);
