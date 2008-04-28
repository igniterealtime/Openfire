-- $Revision$
-- $Date$

INSERT INTO ofVersion (name, version) VALUES ('fastpath', 0);

CREATE TABLE fpWorkgroup(
  workgroupID         INT NOT NULL,
  jid                 VARCHAR(255) NOT NULL,
  displayName         VARCHAR(50) NULL,
  description         VARCHAR(255) NULL,
  status              INT NOT NULL,
  modes               INT NOT NULL,
  creationDate        CHAR(15) NOT NULL,
  modificationDate    CHAR(15) NOT NULL,
  maxchats            INT NOT NULL,
  minchats            INT NOT NULL,
  requestTimeout      INT NOT NULL,
  offerTimeout        INT NOT NULL,
  schedule            VARCHAR(3900) NULL
);
CREATE INDEX fpWorkgroup_workgroupid_idx on fpWorkgroup(workgroupID);

CREATE TABLE fpWorkgroupProp (
  ownerID       INT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     LONG,
  CONSTRAINT fpWorkgroupProp_pk PRIMARY KEY (ownerID,name)
);

CREATE TABLE fpAgent(
  agentID             INT NOT NULL,
  agentJID            VARCHAR(255) NOT NULL,
  name                VARCHAR(255) NULL,
  maxchats            INT NOT NULL,
  minchats            INT NOT NULL
);
CREATE INDEX fpagent_agentid_idx ON fpAgent(agentID);
CREATE INDEX fpagent_agentjid_idx ON fpAgent(agentJID);

CREATE TABLE fpAgentProp (
  ownerID       INT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     VARCHAR(3900) NOT NULL,
  CONSTRAINT fpAgentProp_pk PRIMARY KEY (ownerID,name)
);

CREATE TABLE fpQueue(
  queueID             INT NOT NULL,
  workgroupID         INT NOT NULL,
  name                VARCHAR(50) NOT NULL,
  description         VARCHAR(255) NULL,
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
  name          VARCHAR(100) NOT NULL,
  propValue     VARCHAR(3900) NOT NULL,
  CONSTRAINT fpDispatcherProp_pk PRIMARY KEY (ownerID,name)
);

CREATE TABLE fpDispatcher(
  queueID             INT NOT NULL,
  name                VARCHAR(50) NULL,
  description         VARCHAR(255) NULL,
  offerTimeout        INT NOT NULL,
  requestTimeout      INT NOT NULL,
  CONSTRAINT fpDispatcher_pk PRIMARY KEY (queueID)
);

CREATE TABLE fpQueueProp (
  ownerID       INT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  propValue     VARCHAR(3900) NOT NULL,
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
  groupName    VARCHAR(50) NOT NULL,
  CONSTRAINT jive_fpQueueGroup_pk PRIMARY KEY (queueID,groupName)
);


CREATE TABLE fpSession(
  sessionID      varchar(31) NOT NULL,
  userID         varchar(200) NOT NULL,
  workgroupID    INTEGER NOT NULL,
  transcript     LONG,
  startTime      CHAR(15) NOT NULL,
  endTime        CHAR(15) NOT NULL,
  queueWaitTime  INTEGER,
  state          int NOT NULL,
  caseID         varchar(20),
  status         CHAR(15),
  notes          VARCHAR(4000),
  CONSTRAINT fpSession_pk PRIMARY KEY (sessionID)
);
CREATE INDEX fpsession_workgroupid_idx ON fpSession(workgroupID, userID);
CREATE INDEX fpsession_starttime_idx ON fpSession(workgroupID, startTime);

CREATE TABLE fpAgentSession(
  sessionID varchar(31) NOT NULL,
  agentJID varchar(255) NOT NULL,
  joinTime CHAR(15) NOT NULL,
  leftTime CHAR(15)
);

CREATE TABLE fpSessionMetadata(
  sessionID varchar(31) NOT NULL,
  metadataName varChar(200) NOT NULL,
  metadataValue LONG NOT NULL
);

CREATE TABLE fpSessionProp(
  sessionID     varchar(31) NOT NULL,
  name          varchar(100) NOT NULL,
  propValue     LONG NOT NULL,
  CONSTRAINT fpSessionProp_pk PRIMARY KEY   (sessionID,name)
);

CREATE TABLE fpSetting (
  workgroupName         VARCHAR(100)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  namespace             VARCHAR(255)    NOT NULL,
  value                 LONG            NOT NULL,
  CONSTRAINT fpSetting_pk PRIMARY KEY (workgroupName, name, namespace)
);

CREATE TABLE fpSearchIndex (
  workgroupID         INTEGER NOT NULL,
  lastUpdated         CHAR(15) NOT NULL,
  lastOptimization    CHAR(15) NOT NULL,
  PRIMARY KEY (workgroupID)
);

CREATE TABLE fpWorkgroupRoster (
  workgroupID         INTEGER NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  lastContact         CHAR(15) NULL,
  CONSTRAINT fpWorkgroupRoster_pk PRIMARY KEY (workgroupID, jid)
);
CREATE INDEX fpWrkgrpRoster_wrkgrpjid_idx ON fpWorkgroupRoster(workgroupID);

CREATE TABLE fpChatSetting(
  workgroupNode     VARCHAR2(100),
  type              INTEGER,
  label             VARCHAR2(100),
  description       VARCHAR2(255),
  name              VARCHAR2(100),
  value             LONG,
  defaultValue      VARCHAR2(4000)
);
CREATE INDEX fpChat_workgroupNode_idx ON fpChatSetting(workgroupNode, name);

CREATE TABLE fpOfflineSetting (
  workgroupID   INTEGER NOT NULL,
  redirectPage  VARCHAR2(255),
  emailAddress  VARCHAR2(255),
  subject       VARCHAR2(255),
  offlineText   LONG,
  PRIMARY KEY(workgroupID)
);

CREATE TABLE fpRouteRule (
    workgroupID   INTEGER NOT NULL,
    queueID       INTEGER NOT NULL,
    rulePosition  INT NOT NULL,
    query         LONG
);
