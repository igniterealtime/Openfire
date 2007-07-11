# $Revision:  $
# $Date:  $

# Create PubSub Tables

CREATE TABLE pubsubNode (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  leaf                TINYINT       NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  modificationDate    CHAR(15)      NOT NULL,
  parent              VARCHAR(100)  NULL,
  deliverPayloads     TINYINT       NOT NULL,
  maxPayloadSize      INTEGER       NULL,
  persistItems        TINYINT       NULL,
  maxItems            INTEGER       NULL,
  notifyConfigChanges TINYINT       NOT NULL,
  notifyDelete        TINYINT       NOT NULL,
  notifyRetract       TINYINT       NOT NULL,
  presenceBased       TINYINT       NOT NULL,
  sendItemSubscribe   TINYINT       NOT NULL,
  publisherModel      VARCHAR(15)   NOT NULL,
  subscriptionEnabled TINYINT       NOT NULL,
  configSubscription  TINYINT       NOT NULL,
  contacts            VARCHAR(4000) NULL,
  rosterGroups        VARCHAR(4000) NULL,
  accessModel         VARCHAR(10)   NOT NULL,
  payloadType         VARCHAR(100)  NULL,
  bodyXSLT            VARCHAR(100)  NULL,
  dataformXSLT        VARCHAR(100)  NULL,
  creator             VARCHAR(1024) NOT NULL,
  description         VARCHAR(255)  NULL,
  language            VARCHAR(255)  NULL,
  name                VARCHAR(50)   NULL,
  replyPolicy         VARCHAR(15)   NULL,
  replyRooms          VARCHAR(4000) NULL,
  replyTo             VARCHAR(1024) NULL,
  associationPolicy   VARCHAR(15)   NULL,
  associationTrusted  VARCHAR(4000) NULL,
  maxLeafNodes        INTEGER       NULL,
  PRIMARY KEY (serviceID, nodeID)
);

CREATE TABLE pubsubAffiliation (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  affiliation         VARCHAR(10)   NOT NULL,
  PRIMARY KEY (serviceID, nodeID, jid(70))
);

CREATE TABLE pubsubItem (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  id                  VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  creationDate        CHAR(15)      NOT NULL,
  payload             TEXT          NULL,
  PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubSubscription (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  id                  VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(1024) NOT NULL,
  owner               VARCHAR(1024) NOT NULL,
  state               VARCHAR(15)   NOT NULL,
  deliver             TINYINT       NOT NULL,
  digest              TINYINT       NOT NULL,
  digest_frequency    TINYINT       NOT NULL,
  expire              CHAR(15)      NULL,
  includeBody         TINYINT       NOT NULL,
  showValues          VARCHAR(30)   NOT NULL,
  subscriptionType    VARCHAR(10)   NOT NULL,
  subscriptionDepth   TINYINT       NOT NULL,
  keyword             VARCHAR(200)  NULL,
  PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubDefaultConf (
  serviceID           VARCHAR(100)  NOT NULL,
  leaf                TINYINT       NOT NULL,
  deliverPayloads     TINYINT       NOT NULL,
  maxPayloadSize      INTEGER       NOT NULL,
  persistItems        TINYINT       NOT NULL,
  maxItems            INTEGER       NOT NULL,
  notifyConfigChanges TINYINT       NOT NULL,
  notifyDelete        TINYINT       NOT NULL,
  notifyRetract       TINYINT       NOT NULL,
  presenceBased       TINYINT       NOT NULL,
  sendItemSubscribe   TINYINT       NOT NULL,
  publisherModel      VARCHAR(15)   NOT NULL,
  subscriptionEnabled TINYINT       NOT NULL,
  accessModel         VARCHAR(10)   NOT NULL,
  language            VARCHAR(255)  NULL,
  replyPolicy         VARCHAR(15)   NULL,
  associationPolicy   VARCHAR(15)   NOT NULL,
  maxLeafNodes        INTEGER       NOT NULL,
  PRIMARY KEY (serviceID, leaf)
);

UPDATE jiveVersion set version=7 where name = 'openfire';