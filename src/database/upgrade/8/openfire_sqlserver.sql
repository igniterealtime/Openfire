
/* Drop old columns of pubSubNode */
ALTER TABLE pubsubNode DROP COLUMN contacts;
ALTER TABLE pubsubNode DROP COLUMN rosterGroups;
ALTER TABLE pubsubNode DROP COLUMN replyRooms;
ALTER TABLE pubsubNode DROP COLUMN replyTo;
ALTER TABLE pubsubNode DROP COLUMN associationTrusted;

/* Reduce columns size to avoid warnings */
ALTER TABLE pubsubNode ALTER COLUMN creator NVARCHAR(255)  NOT NULL;

/* Reduce columns size to avoid warnings */
ALTER TABLE pubsubAffiliation DROP CONSTRAINT pubsubAffil_pk;
ALTER TABLE pubsubAffiliation ALTER COLUMN jid NVARCHAR(250) NOT NULL;
ALTER TABLE pubsubAffiliation ADD CONSTRAINT pubsubAffil_pk PRIMARY KEY (serviceID, nodeID, jid);

/* Add new pubsub tables */
CREATE TABLE pubsubNodeJIDs (
  serviceID           NVARCHAR(100)  NOT NULL,
  nodeID              NVARCHAR(100)  NOT NULL,
  jid                 NVARCHAR(250) NOT NULL,
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
