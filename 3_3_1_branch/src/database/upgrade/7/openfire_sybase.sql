/* $Revision:  $ */
/* $Date:  $ */

/* Create PubSub Tables */

CREATE TABLE pubsubNode (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  leaf                INT            NOT NULL,
  creationDate        CHAR(15)       NOT NULL,
  modificationDate    CHAR(15)       NOT NULL,
  parent              NVARCHAR(100)  NULL,
  deliverPayloads     INT            NOT NULL,
  maxPayloadSize      INT            NULL,
  persistItems        INT            NULL,
  maxItems            INT            NULL,
  notifyConfigChanges INT            NOT NULL,
  notifyDelete        INT            NOT NULL,
  notifyRetract       INT            NOT NULL,
  presenceBased       INT            NOT NULL,
  sendItemSubscribe   INT            NOT NULL,
  publisherModel      NVARCHAR(15)   NOT NULL,
  subscriptionEnabled INT            NOT NULL,
  configSubscription  INT            NOT NULL,
  contacts            NVARCHAR(4000) NULL,
  rosterGroups        NVARCHAR(4000) NULL,
  accessModel         NVARCHAR(10)   NOT NULL,
  payloadType         NVARCHAR(100)  NULL,
  bodyXSLT            NVARCHAR(100)  NULL,
  dataformXSLT        NVARCHAR(100)  NULL,
  creator             NVARCHAR(1024) NOT NULL,
  description         NVARCHAR(255)  NULL,
  language            NVARCHAR(255)  NULL,
  name                NVARCHAR(50)   NULL,
  replyPolicy         NVARCHAR(15)   NULL,
  replyRooms          NVARCHAR(4000) NULL,
  replyTo             NVARCHAR(1024) NULL,
  associationPolicy   NVARCHAR(15)   NULL,
  associationTrusted  NVARCHAR(4000) NULL,
  maxLeafNodes        INT            NULL,
  CONSTRAINT pubsubNode_pk PRIMARY KEY (serviceID, nodeID)
);

CREATE TABLE pubsubAffiliation (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  jid                 NVARCHAR(1024) NOT NULL,
  affiliation         NVARCHAR(10)   NOT NULL,
  CONSTRAINT pubsubAffil_pk PRIMARY KEY (serviceID, nodeID, jid)
);

CREATE TABLE pubsubItem (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  id                  NVARCHAR(100)  NOT NULL,
  jid                 NVARCHAR(1024) NOT NULL,
  creationDate        CHAR(15)       NOT NULL,
  payload             TEXT           NULL,
  CONSTRAINT pubsubItem_pk PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubSubscription (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  id                  NVARCHAR(100)  NOT NULL,
  jid                 NVARCHAR(1024) NOT NULL,
  owner               NVARCHAR(1024) NOT NULL,
  state               NVARCHAR(15)   NOT NULL,
  deliver             INT            NOT NULL,
  digest              INT            NOT NULL,
  digest_frequency    INT            NOT NULL,
  expire              CHAR(15)       NULL,
  includeBody         INT            NOT NULL,
  showValues          NVARCHAR(30)   NOT NULL,
  subscriptionType    NVARCHAR(10)   NOT NULL,
  subscriptionDepth   INT            NOT NULL,
  keyword             NVARCHAR(200)  NULL,
  CONSTRAINT pubsubSubs_pk PRIMARY KEY (serviceID, nodeID, id)
);

CREATE TABLE pubsubDefaultConf (
  serviceID           NVARCHAR(100) NOT NULL,
  leaf                INT           NOT NULL,
  deliverPayloads     INT           NOT NULL,
  maxPayloadSize      INT           NOT NULL,
  persistItems        INT           NOT NULL,
  maxItems            INT           NOT NULL,
  notifyConfigChanges INT           NOT NULL,
  notifyDelete        INT           NOT NULL,
  notifyRetract       INT           NOT NULL,
  presenceBased       INT           NOT NULL,
  sendItemSubscribe   INT           NOT NULL,
  publisherModel      NVARCHAR(15)  NOT NULL,
  subscriptionEnabled INT           NOT NULL,
  accessModel         NVARCHAR(10)  NOT NULL,
  language            NVARCHAR(255) NULL,
  replyPolicy         NVARCHAR(15)  NULL,
  associationPolicy   NVARCHAR(15)  NOT NULL,
  maxLeafNodes        INT           NOT NULL,
  CONSTRAINT pubsubDefConf_pk PRIMARY KEY (serviceID, leaf)
);

UPDATE jiveVersion set version=7 where name = 'openfire';