// $Revision$
// $Date$

INSERT INTO ofVersion (name, version) VALUES ('fastpath', 0);

CREATE TABLE fpWorkgroup (
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
  schedule            varchar(4000) NULL,
  PRIMARY KEY (workgroupID)
);
CREATE INDEX fpWorkgroup_workgroupid_idx ON fpWorkgroup (workgroupID);

CREATE TABLE fpWorkgroupProp (
  ownerID       BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     VARCHAR(4000) NOT NULL,
  PRIMARY KEY   (ownerID,name)
);

CREATE TABLE fpAgent (
  agentID             BIGINT NOT NULL,
  agentJID            VARCHAR(255) NOT NULL,
  name                VARCHAR(255) NULL,
  maxchats            INTEGER NOT NULL,
  minchats            INTEGER NOT NULL,
  PRIMARY KEY (agentJID)
);
CREATE INDEX fpAgent_agentid_idx ON fpAgent(agentID);
CREATE INDEX fpAgent_agentjid_idx ON fpAgent(agentJID);

CREATE TABLE fpAgentProp (
  ownerID       BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     VARCHAR(4000) NOT NULL,
  PRIMARY KEY   (ownerID,name)
);

CREATE TABLE fpQueue (
  queueID             BIGINT NOT NULL,
  workgroupID         BIGINT NOT NULL,
  name                VARCHAR(50) NOT NULL,
  description         VARCHAR(255) NULL,
  priority            INTEGER NOT NULL,
  maxchats            INTEGER NOT NULL,
  minchats            INTEGER NOT NULL,
  overflow            INTEGER NOT NULL,
  backupQueue         BIGINT NULL,
  PRIMARY KEY (workgroupID,queueID)
);
CREATE INDEX fpQueue_workgroupid_idx ON fpQueue(workgroupID);
CREATE INDEX fpQueue_queueid_idx ON fpQueue(queueID);

CREATE TABLE fpDispatcherProp (
  ownerID       BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     varchar(4000) NOT NULL,
  PRIMARY KEY   (ownerID,name)
);

CREATE TABLE fpDispatcher (
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
  propValue     VARCHAR(4000) NOT NULL,
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
  groupName    VARCHAR(1024),
  PRIMARY KEY (queueID, groupName)
);

CREATE TABLE fpSession (
  sessionID      VARCHAR(31) NOT NULL,
  userID         VARCHAR(200) NOT NULL,
  workgroupID    BIGINT NOT NULL,
  transcript     LONGVARCHAR,
  startTime      CHAR(15) NOT NULL,
  endTime        CHAR(15) NOT NULL,
  queueWaitTime  BIGINT,
  state          INTEGER NOT NULL,
  caseID         VARCHAR(20),
  status         CHAR(15),
  notes          LONGVARCHAR,
  PRIMARY KEY (sessionID)
);
CREATE INDEX fpSession_workgroupid_idx ON fpSession(workgroupID, userID);
CREATE INDEX fpSession_starttime_idx ON fpSession(workgroupID, startTime);

CREATE TABLE fpAgentSession (
  sessionID     VARCHAR(31) NOT NULL,
  agentJID      VARCHAR(255) NOT NULL,
  joinTime      CHAR(15) NOT NULL,
  leftTime      CHAR(15)
);
CREATE INDEX fpAgentSession_sessionid_idx ON fpAgentSession(sessionID);

CREATE TABLE fpSessionMetadata (
  sessionID     VARCHAR(31) NOT NULL,
  metadataName  VARCHAR(200) NOT NULL,
  metadataValue VARCHAR(200) NOT NULL
);

CREATE TABLE fpSessionProp (
  sessionID     VARCHAR(31) NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     LONGVARCHAR NOT NULL,
  PRIMARY KEY   (sessionID,name)
);

CREATE TABLE fpSetting (
  workgroupName         VARCHAR(100)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(255)    NOT NULL,
  value                 LONGVARCHAR     NOT NULL,
  PRIMARY KEY (workgroupName, name, namespace)
);

CREATE TABLE fpSearchIndex (
  workgroupID         BIGINT NOT NULL,
  lastUpdated         CHAR(15) NOT NULL,
  lastOptimization    CHAR(15) NOT NULL,
  PRIMARY KEY (workgroupID)
);

CREATE TABLE fpWorkgroupRoster (
  workgroupID         BIGINT NOT NULL,
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
  value             LONGVARCHAR,
  defaultValue      LONGVARCHAR
);
CREATE INDEX fpChatSetting_idx ON fpChatSetting(workgroupNode, name);

CREATE TABLE fpOfflineSetting (
  workgroupID BIGINT NOT NULL,
  redirectPage varChar(255),
  emailAddress varChar(255),
  subject varChar(255),
  offlineText LONGVARCHAR,
  PRIMARY KEY(workgroupID)
);

CREATE TABLE fpRouteRule (
    workgroupID   BIGINT NOT NULL,
    queueID       BIGINT NOT NULL,
    rulePosition  INT NOT NULL,
    query         LONGVARCHAR
);
