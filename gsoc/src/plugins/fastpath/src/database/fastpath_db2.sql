-- $Revision$
-- $Date$

INSERT INTO ofVersion (name, version) VALUES ('fastpath', 0);

CREATE TABLE fpWorkgroup(
    workgroupID INTEGER NOT NULL,
    jid VARCHAR(255) NOT NULL,
    displayName VARCHAR(50),
    description VARCHAR(255) ,
    chatserver VARCHAR(255) ,
    status INTEGER NOT NULL,
    modes INTEGER NOT NULL,
    creationDate VARCHAR(15) NOT NULL,
    modificationDate VARCHAR(15) NOT NULL,
    maxchats INTEGER NOT NULL,
    minchats INTEGER NOT NULL,
    requestTimeout INTEGER NOT NULL,
    offerTimeout INTEGER NOT NULL,
    schedule LONG VARCHAR,
    CONSTRAINT fpworkgroup_pk PRIMARY KEY (workgroupID)
);

-- CREATE INDEX fpWg_wgid_idx ON fpWorkgroup (workgroupID);

CREATE TABLE fpWorkgroupProp (
    ownerID INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    propValue LONG VARCHAR NOT NULL,
    CONSTRAINT fpWGProp_pk PRIMARY KEY (ownerID,name)
);

CREATE TABLE fpAgent(
    agentID INTEGER NOT NULL,
    agentJID VARCHAR(255) NOT NULL,
    name VARCHAR(255) ,
    maxchats INTEGER NOT NULL,
    minchats INTEGER NOT NULL,
    CONSTRAINT fpAgent_pk PRIMARY KEY (agentJID)
);
CREATE INDEX fpagt_agtid_idx ON fpagent(agentID);
-- CREATE INDEX fpagt_agtjid_idx ON fpagent(agentJID);

CREATE TABLE fpAgentProp (
    ownerID INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    propValue LONG VARCHAR NOT NULL,
    CONSTRAINT fpAgentProp_pk PRIMARY KEY (ownerID,name)
);

CREATE TABLE fpQueue(
    queueID INTEGER NOT NULL,
    workgroupID INTEGER NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255) ,
    priority INTEGER NOT NULL,
    maxchats INTEGER NOT NULL,
    minchats INTEGER NOT NULL,
    overflow INTEGER NOT NULL,
    backupQueue INTEGER ,
    CONSTRAINT fpQueue_pk PRIMARY KEY (workgroupID,queueID)
);

CREATE INDEX fpqueue_wgid_idx ON fpqueue(workgroupID);
CREATE INDEX fpqueue_qid_idx ON fpqueue(queueID);

CREATE TABLE fpDispatcherProp (
    ownerID INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    propValue LONG VARCHAR NOT NULL,
    CONSTRAINT fpDispatProp_pk PRIMARY KEY (ownerID,name)
);


CREATE TABLE fpDispatcher(
    queueID INTEGER NOT NULL,
    name VARCHAR(50) ,
    description VARCHAR(255) ,
    offerTimeout INTEGER NOT NULL,
    requestTimeout INTEGER NOT NULL,
    CONSTRAINT fpDispatcher_pk PRIMARY KEY (queueID)
);

CREATE TABLE fpQueueProp (
    ownerID INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    propValue LONG VARCHAR NOT NULL,
    CONSTRAINT fpQueueProp_pk PRIMARY KEY (ownerID,name)
);

CREATE TABLE fpQueueAgent (
    queueID INTEGER NOT NULL,
    objectID INTEGER NOT NULL,
    objectType INTEGER NOT NULL,
    administrator INTEGER,
    CONSTRAINT fpQueueAgent_pk PRIMARY KEY (queueID,objectID,objectType)
);

CREATE TABLE fpQueueGroup (
    queueID  INTEGER NOT NULL,
    groupName varChar(1024) NOT NULL
);

CREATE TABLE fpSession(
    sessionID varchar(31) NOT NULL,
    userID varchar(200) NOT NULL,
    workgroupID INTEGER NOT NULL,
    transcript LONG VARCHAR,
    startTime CHAR(15) NOT NULL,
    endTime CHAR(15) NOT NULL,
    queueWaitTime INTEGER,
    state INTEGER NOT NULL,
    caseID varchar(20),
    status CHAR(15),
    notes LONG VARCHAR,
    CONSTRAINT fpSession_pk PRIMARY KEY (sessionID)
);

CREATE INDEX fpses_wgid_idx ON fpSession(workgroupID, userID);
CREATE INDEX fpses_st_idx ON fpSession(workgroupID, startTime);

CREATE TABLE fpAgentSession(
    sessionID varchar(31) NOT NULL,
    agentJID varchar(255) NOT NULL,
    joinTime CHAR(15) NOT NULL,
    leftTime CHAR(15)
);
-- CREATE INDEX fpagtss_sesid_idx ON fpSession(sessionID);

CREATE TABLE fpSessionMetadata(
    sessionID varchar(31) NOT NULL,
    metadataName varChar(200) NOT NULL,
    metadataValue LONG VARCHAR NOT NULL
);

CREATE TABLE fpSessionProp(
    sessionID varchar(31) NOT NULL,
    name varchar(100) NOT NULL,
    propValue LONG VARCHAR NOT NULL,
    CONSTRAINT fpSessionProp_pk PRIMARY KEY (sessionID,name)
);

CREATE TABLE fpSetting (
    workgroupName VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    namespace VARCHAR(255) NOT NULL,
    value LONG VARCHAR NOT NULL,
    CONSTRAINT fpSetting_pk PRIMARY KEY (workgroupName, name, namespace)
);

CREATE TABLE fpSearchIndex (
    workgroupID INTEGER NOT NULL,
    lastUpdated CHAR(15) NOT NULL,
    lastOptimization CHAR(15) NOT NULL,
    CONSTRAINT fpSearchIndex_pk PRIMARY KEY (workgroupID)
);

CREATE TABLE fpWorkgroupRoster (
    workgroupID INTEGER NOT NULL,
    jid VARCHAR(255) NOT NULL,
    lastContact CHAR(15),
    CONSTRAINT fpRoster_pk PRIMARY KEY (workgroupID, jid)
);
CREATE INDEX fpWgRst_wgjid_idx ON fpWorkgroupRoster(workgroupID);


CREATE TABLE fpChatSetting(
    workgroupNode varchar(100),
    type INTEGER,
    label varchar(100),
    description varchar(255),
    name varchar(100),
    value LONG VARCHAR,
    defaultValue LONG VARCHAR
);
CREATE INDEX fpChatSetting_idx ON fpChatSetting(workgroupNode, name);

CREATE TABLE fpOfflineSetting (
    workgroupID INTEGER NOT NULL,
    redirectPage varChar(255),
    emailAddress varChar(255),
    subject varChar(255),
    offlineText LONG VARCHAR,
    CONSTRAINT fpOfflineSet_pk PRIMARY KEY(workgroupID)
);

CREATE TABLE fpRouteRule (
    workgroupID INTEGER NOT NULL,
    queueID     INTEGER NOT NULL,
    rulePosition   INTEGER NOT NULL,
    query      LONG VARCHAR
);
