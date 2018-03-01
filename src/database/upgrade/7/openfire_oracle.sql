
-- Create PubSub Tables

CREATE TABLE pubsubNode (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  leaf                INTEGER        NOT NULL,
  creationDate        CHAR(15)       NOT NULL,
  modificationDate    CHAR(15)       NOT NULL,
  parent              VARCHAR2(100)  NULL,
  deliverPayloads     INTEGER        NOT NULL,
  maxPayloadSize      INTEGER        NULL,
  persistItems        INTEGER        NULL,
  maxItems            INTEGER        NULL,
  notifyConfigChanges INTEGER        NOT NULL,
  notifyDelete        INTEGER        NOT NULL,
  notifyRetract       INTEGER        NOT NULL,
  presenceBased       INTEGER        NOT NULL,
  sendItemSubscribe   INTEGER        NOT NULL,
  publisherModel      VARCHAR2(15)   NOT NULL,
  subscriptionEnabled INTEGER        NOT NULL,
  configSubscription  INTEGER        NOT NULL,
  contacts            VARCHAR2(4000) NULL,
  rosterGroups        VARCHAR2(4000) NULL,
  accessModel         VARCHAR2(10)   NOT NULL,
  payloadType         VARCHAR2(100)  NULL,
  bodyXSLT            VARCHAR2(100)  NULL,
  dataformXSLT        VARCHAR2(100)  NULL,
  creator             VARCHAR2(1024) NOT NULL,
  description         VARCHAR2(255)  NULL,
  language            VARCHAR2(255)  NULL,
  name                VARCHAR2(50)   NULL,
  replyPolicy         VARCHAR2(15)   NULL,
  replyRooms          VARCHAR2(4000) NULL,
  replyTo             VARCHAR2(1024) NULL,
  associationPolicy   VARCHAR2(15)   NULL,
  associationTrusted  VARCHAR2(4000) NULL,
  maxLeafNodes        INTEGER        NULL,
  CONSTRAINT pubsubNode_pk PRIMARY KEY (serviceID, nodeID)
);

CREATE TABLE pubsubAffiliation (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  affiliation         VARCHAR2(10)   NOT NULL,
  CONSTRAINT pubsubAffil_pk PRIMARY KEY (serviceID, nodeID, jid)
);

CREATE TABLE pubsubItem (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  id                  VARCHAR2(100)  NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  creationDate        CHAR(15)       NOT NULL,
  payload             VARCHAR(4000)  NULL,
  CONSTRAINT pubsubItem_pk PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubSubscription (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  id                  VARCHAR2(100)  NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  owner               VARCHAR2(1024) NOT NULL,
  state               VARCHAR(15)    NOT NULL,
  deliver             INTEGER        NOT NULL,
  digest              INTEGER        NOT NULL,
  digest_frequency    INTEGER        NOT NULL,
  expire              CHAR(15)       NULL,
  includeBody         INTEGER        NOT NULL,
  showValues          VARCHAR(30)    NOT NULL,
  subscriptionType    VARCHAR(10)    NOT NULL,
  subscriptionDepth   INTEGER        NOT NULL,
  keyword             VARCHAR2(200)  NULL,
  CONSTRAINT pubsubSubs_pk PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubDefaultConf (
  serviceID           VARCHAR2(100) NOT NULL,
  leaf                INTEGER       NOT NULL,
  deliverPayloads     INTEGER       NOT NULL,
  maxPayloadSize      INTEGER       NOT NULL,
  persistItems        INTEGER       NOT NULL,
  maxItems            INTEGER       NOT NULL,
  notifyConfigChanges INTEGER       NOT NULL,
  notifyDelete        INTEGER       NOT NULL,
  notifyRetract       INTEGER       NOT NULL,
  presenceBased       INTEGER       NOT NULL,
  sendItemSubscribe   INTEGER       NOT NULL,
  publisherModel      VARCHAR2(15)  NOT NULL,
  subscriptionEnabled INTEGER       NOT NULL,
  accessModel         VARCHAR2(10)  NOT NULL,
  language            VARCHAR2(255) NULL,
  replyPolicy         VARCHAR2(15)  NULL,
  associationPolicy   VARCHAR2(15)  NOT NULL,
  maxLeafNodes        INTEGER       NOT NULL,
  CONSTRAINT pubsubDefConf_pk PRIMARY KEY (serviceID, leaf)
);

UPDATE jiveVersion set version=7 where name = 'openfire';

commit;
