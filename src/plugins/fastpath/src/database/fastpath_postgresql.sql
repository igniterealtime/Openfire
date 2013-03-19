-- $Revision$
-- $Date$

INSERT INTO ofVersion (name, version) VALUES ('fastpath', 0);

CREATE TABLE fpWorkgroup(
  workgroupID         INTEGER      NOT NULL,
  jid                 VARCHAR(255) NOT NULL,
  displayName         VARCHAR(50)  NULL,
  description         VARCHAR(255) NULL,
  status              INTEGER      NOT NULL,
  modes               INTEGER      NOT NULL,
  creationDate        VARCHAR(15)  NOT NULL,
  modificationDate    VARCHAR(15)  NOT NULL,
  maxchats            INTEGER      NOT NULL,
  minchats            INTEGER      NOT NULL,
  requestTimeout      INTEGER      NOT NULL,
  offerTimeout        INTEGER      NOT NULL,
  schedule            TEXT         NULL,
  PRIMARY KEY (workgroupID)
);
CREATE INDEX fpWorkgroup_workgroupid_idx ON fpWorkgroup (workgroupID);

CREATE TABLE fpWorkgroupProp (
  ownerID       INTEGER      NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT         NOT NULL,
  PRIMARY KEY   (ownerID,name)
);

CREATE TABLE fpAgent(
  agentID             INTEGER      NOT NULL,
  agentJID            VARCHAR(255) NOT NULL,
  name                VARCHAR(255) NULL,
  maxchats            INTEGER      NOT NULL,
  minchats            INTEGER      NOT NULL,
  PRIMARY KEY (agentJID)
);
CREATE INDEX fpagent_agentid_idx ON fpagent(agentID);
CREATE INDEX fpagent_agentjid_idx ON fpagent(agentJID);

CREATE TABLE fpAgentProp (
  ownerID       INTEGER      NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT         NOT NULL,
  PRIMARY KEY   (ownerID,name)
);

CREATE TABLE fpQueue(
  queueID             INTEGER      NOT NULL,
  workgroupID         INTEGER      NOT NULL,
  name                VARCHAR(50)  NOT NULL,
  description         VARCHAR(255) NULL,
  priority            INTEGER      NOT NULL,
  maxchats            INTEGER      NOT NULL,
  minchats            INTEGER      NOT NULL,
  overflow            INTEGER      NOT NULL,
  backupQueue         INTEGER      NULL,
  PRIMARY KEY (workgroupID,queueID)
);
CREATE INDEX fpqueue_workgroupid_idx ON fpqueue(workgroupID);
CREATE INDEX fpqueue_queueid_idx ON fpqueue(queueID);

CREATE TABLE fpDispatcherProp (
  ownerID       INTEGER      NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT         NOT NULL,
  PRIMARY KEY   (ownerID,name)
);

CREATE TABLE fpDispatcher(
  queueID             INTEGER      NOT NULL,
  name                VARCHAR(50)  NULL,
  description         VARCHAR(255) NULL,
  offerTimeout        INTEGER      NOT NULL,
  requestTimeout      INTEGER      NOT NULL,
  PRIMARY KEY (queueID)
);

CREATE TABLE fpQueueProp (
  ownerID       INTEGER      NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     TEXT         NOT NULL,
  PRIMARY KEY   (ownerID,name)
);

CREATE TABLE fpQueueAgent (
  queueID        INTEGER NOT NULL,
  objectID       INTEGER NOT NULL,
  objectType     INTEGER NOT NULL,
  administrator  INTEGER NULL,
  PRIMARY KEY   (queueID,objectID,objectType)
);

CREATE TABLE fpQueueGroup (
  queueID      INTEGER NOT NULL,
  groupName    VARCHAR(50) NOT NULL,
  PRIMARY KEY (queueID,groupName)
);

CREATE TABLE fpSession(
  sessionID      varchar(31)  NOT NULL,
  userID         varchar(200) NOT NULL,
  workgroupID    INTEGER      NOT NULL,
  transcript     TEXT,
  startTime      CHAR(15)     NOT NULL,
  endTime        CHAR(15)     NOT NULL,
  queueWaitTime  INTEGER,
  state          INTEGER      NOT NULL,
  caseID         varchar(20),
  status         CHAR(15),
  notes          TEXT,
  PRIMARY KEY (sessionID)
);
CREATE INDEX fpsession_workgroupid_idx ON fpSession(workgroupID, userID);
CREATE INDEX fpsession_starttime_idx ON fpSession(workgroupID, startTime);

CREATE TABLE fpAgentSession(
  sessionID varchar(31)  NOT NULL,
  agentJID  varchar(255) NOT NULL,
  joinTime  CHAR(15)     NOT NULL,
  leftTime  CHAR(15)
);
CREATE INDEX fpagentsession_sessionid_idx ON fpSession(sessionID);

CREATE TABLE fpSessionMetadata(
  sessionID     varchar(31)  NOT NULL,
  metadataName  varChar(200) NOT NULL,
  metadataValue varChar(200) NOT NULL
);

CREATE TABLE fpSessionProp(
  sessionID     varchar(31)  NOT NULL,
  name          varchar(100) NOT NULL,
  propValue     TEXT         NOT NULL,
  PRIMARY KEY   (sessionID,name)
);

CREATE TABLE fpSetting (
  workgroupName         VARCHAR(255)   NOT NULL,
  name                  VARCHAR(100)   NOT NULL,
  namespace             VARCHAR(255)   NOT NULL,
  value                 TEXT           NOT NULL,
  PRIMARY KEY (workgroupName, name, namespace)
);

CREATE TABLE fpSearchIndex (
  workgroupID         INTEGER NOT NULL,
  lastUpdated         CHAR(15) NOT NULL,
  lastOptimization    CHAR(15) NOT NULL,
  PRIMARY KEY (workgroupID)
);

CREATE TABLE fpWorkgroupRoster (
  workgroupID         INTEGER NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  lastContact         CHAR(15) NULL,
  PRIMARY KEY (workgroupID, jid)
);
CREATE INDEX fpWrkgrpRoster_workgroupjid_idx ON fpWorkgroupRoster(workgroupID);

CREATE TABLE fpChatSetting (
  workgroupNode     varchar(100),
  type              INTEGER,
  label             varchar(100),
  description       varchar(255),
  name              varchar(100),
  value             TEXT,
  defaultValue      TEXT
);
CREATE INDEX fpChatSettings_idx ON fpChatSetting(workgroupNode, name);

CREATE TABLE fpOfflineSetting (
  workgroupID      INTEGER NOT NULL,
  redirectPage     varChar(255),
  emailAddress     varChar(255),
  subject          varChar(255),
  offlineText      TEXT,
  PRIMARY KEY(workgroupID)
);

CREATE TABLE fpRouteRule (
    workgroupID   INTEGER NOT NULL,
    queueID       INTEGER NOT NULL,
    rulePosition  INTEGER NOT NULL,
    query         TEXT
);
