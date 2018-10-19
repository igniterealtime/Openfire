
/* Drop old columns of pubSubNode */
ALTER TABLE pubsubNode DROP COLUMN contacts;
ALTER TABLE pubsubNode DROP COLUMN rosterGroups;
ALTER TABLE pubsubNode DROP COLUMN replyRooms;
ALTER TABLE pubsubNode DROP COLUMN replyTo;
ALTER TABLE pubsubNode DROP COLUMN associationTrusted;

/* Add new pubsub tables */
CREATE TABLE pubsubNodeJIDs (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  jid                 NVARCHAR(1024) NOT NULL,
  associationType     NVARCHAR(20)   NOT NULL,
  CONSTRAINT pubsubJID_pk PRIMARY KEY (serviceID, nodeID, jid)
);

CREATE TABLE pubsubNodeGroups (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  rosterGroup         NVARCHAR(100)  NOT NULL
);
CREATE INDEX pubsubNodeGroups_idx ON pubsubNodeGroups (serviceID, nodeID);

UPDATE jiveVersion set version=8 where name = 'openfire';
