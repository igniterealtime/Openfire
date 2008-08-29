/* $Revision$   */
/* $Date$       */

INSERT INTO ofVersion (name, version) VALUES ('fastpath', 0);

CREATE TABLE fpWorkgroup(
  workgroupID         INT NOT NULL,
  jid                 VARCHAR(255) NOT NULL,
  displayName         NVARCHAR(50) NULL,
  description         NVARCHAR(255) NULL,
  status              INT NOT NULL,
  modes               INT NOT NULL,
  creationDate        CHAR(15) NOT NULL,
  modificationDate    CHAR(15) NOT NULL,
  maxchats            INT NOT NULL,
  minchats            INT NOT NULL,
  requestTimeout      INT NOT NULL,
  offerTimeout        INT NOT NULL,
  schedule            NVARCHAR(3400) NULL,
  CONSTRAINT fpWorkgroup_pk PRIMARY KEY (workgroupID)
);
CREATE INDEX fpWorkgroup_workgroupid_idx on fpWorkgroup(workgroupID);

CREATE TABLE fpWorkgroupProp (
  ownerID       INT NOT NULL,
  name          NVARCHAR(100) NOT NULL,
  propValue     TEXT,
  CONSTRAINT fpWorkgroupProp_pk PRIMARY KEY (ownerID,name)
);

CREATE TABLE fpAgent(
  agentID             INT NOT NULL,
  agentJID            NVARCHAR(255) NOT NULL,
  name                NVARCHAR(255) NULL,
  maxchats            INT NOT NULL,
  minchats            INT NOT NULL,
  CONSTRAINT fpAgent_pk PRIMARY KEY (agentJID)
);
CREATE INDEX fpagent_agentid_idx ON fpAgent(agentID);
CREATE INDEX fpagent_agentjid_idx ON fpAgent(agentJID);

CREATE TABLE fpAgentProp (
  ownerID       INT NOT NULL,
  name          NVARCHAR(100) NOT NULL,
  propValue     NVARCHAR(3900) NOT NULL,
  CONSTRAINT fpAgentProp_pk PRIMARY KEY (ownerID,name)
);

CREATE TABLE fpQueue(
  queueID             INT NOT NULL,
  workgroupID         INT NOT NULL,
  name                NVARCHAR(50) NOT NULL,
  description         NVARCHAR(255) NULL,
  priority            INT NOT NULL,
  maxchats            INT NOT NULL,
  minchats            INT NOT NULL,
  overflow            INT NOT NULL,
  backupQueue         INT NULL,
  CONSTRAINT fpQueue_pk PRIMARY KEY (workgroupID,queueID)
);
CREATE INDEX fpqueue_workgroupid_idx ON fpQueue(workgroupID);
CREATE INDEX fpqueue_queueid_idx ON fpQueue(queueID);

CREATE TABLE fpDispatcherProp (
  ownerID       INT NOT NULL,
  name          NVARCHAR(100) NOT NULL,
  propValue     NVARCHAR(3900) NOT NULL,
  CONSTRAINT fpDispatcherProp_pk PRIMARY KEY (ownerID,name)
);

CREATE TABLE fpDispatcher(
  queueID             INT NOT NULL,
  name                NVARCHAR(50) NULL,
  description         NVARCHAR(255) NULL,
  offerTimeout        INT NOT NULL,
  requestTimeout      INT NOT NULL,
  CONSTRAINT fpDispatcher_pk PRIMARY KEY (queueID)
);

CREATE TABLE fpQueueProp (
  ownerID       INT NOT NULL,
  name          NVARCHAR(100) NOT NULL,
  propValue     NVARCHAR(3900) NOT NULL,
  CONSTRAINT fpQueueProp_pk PRIMARY KEY (ownerID,name)
);

CREATE TABLE fpQueueAgent (
  queueID        INT NOT NULL,
  objectID       INT NOT NULL,
  objectType     INT NOT NULL,
  administrator  INT NULL,
  CONSTRAINT jive_fpGroupQueue_pk PRIMARY KEY (queueID,objectID,objectType)
);

CREATE TABLE fpQueueGroup (
  queueID      INT NOT NULL,
  groupName    NVARCHAR(50) NOT NULL,
  CONSTRAINT jive_fpQueueAgent_pk PRIMARY KEY  (queueID,groupName)
);

CREATE TABLE fpSession(
  sessionID      NVARCHAR(31) NOT NULL,
  userID         NVARCHAR(200) NOT NULL,
  workgroupID    INT NOT NULL,
  transcript     TEXT,
  startTime      CHAR(15) NOT NULL,
  endTime        CHAR(15) NOT NULL,
  queueWaitTime  INT,
  state          int NOT NULL,
  caseID         NVARCHAR(20),
  status         CHAR(15),
  notes          TEXT,
  CONSTRAINT fpSession_pk PRIMARY KEY (sessionID)
);
CREATE INDEX fpsession_workgroupid_idx ON fpSession(workgroupID, userID);
CREATE INDEX fpsession_starttime_idx ON fpSession(workgroupID, startTime);

CREATE TABLE fpAgentSession(
  sessionID NVARCHAR(31) NOT NULL,
  agentJID NVARCHAR(255) NOT NULL,
  joinTime CHAR(15) NOT NULL,
  leftTime CHAR(15)
);
CREATE INDEX fpagentsession_sessionid_idx ON fpSession(sessionID);

CREATE TABLE fpSessionMetadata(
  sessionID NVARCHAR(31) NOT NULL,
  metadataName NVARCHAR(200) NOT NULL,
  metadataValue TEXT NOT NULL
);

CREATE TABLE fpSessionProp(
  sessionID     NVARCHAR(31) NOT NULL,
  name          NVARCHAR(100) NOT NULL,
  propValue     TEXT NOT NULL,
  CONSTRAINT fpSessionProp_pk PRIMARY KEY   (sessionID,name)
);

CREATE TABLE fpSetting (
  workgroupName         NVARCHAR(100)     NOT NULL,
  name                  NVARCHAR(100)    NOT NULL,
  namespace             NVARCHAR(245)    NOT NULL,
  value                 TEXT            NOT NULL,
  CONSTRAINT fpSetting_pk PRIMARY KEY (workgroupName, name, namespace)
);

CREATE TABLE fpSearchIndex (
  workgroupID         INT NOT NULL,
  lastUpdated         CHAR(15) NOT NULL,
  lastOptimization    CHAR(15) NOT NULL,
  CONSTRAINT fpSearchIndex_pk PRIMARY KEY (workgroupID)
);

CREATE TABLE fpWorkgroupRoster (
  workgroupID         INT NOT NULL,
  jid                 NVARCHAR(444) NOT NULL,
  lastContact         CHAR(15) NULL,
  CONSTRAINT fpWorkgroupRoster_pk PRIMARY KEY (workgroupID, jid)
);
CREATE INDEX fpWrkgrpRoster_workgroupjid_idx ON fpWorkgroupRoster(workgroupID);

CREATE TABLE fpChatSetting (
  workgroupNode     NVARCHAR(100),
  type              INT,
  label             NVARCHAR(100),
  description       NVARCHAR(255),
  name              NVARCHAR(100),
  value             TEXT,
  defaultValue      TEXT
);
CREATE INDEX fpChatSetting_idx ON fpChatSetting(workgroupNode, name);

CREATE TABLE fpOfflineSetting (
  workgroupID       INT NOT NULL,
  redirectPage      NVARCHAR(255),
  emailAddress      NVARCHAR(255),
  subject           NVARCHAR(255),
  offlineText       TEXT,
  CONSTRAINT fpOfflineSetting_pk PRIMARY KEY(workgroupID)
);

CREATE TABLE fpRouteRule (
    workgroupID   INT NOT NULL,
    queueID       INT NOT NULL,
    rulePosition  INT NOT NULL,
    query         TEXT
);
