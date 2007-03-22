# $Revision:  $
# $Date:  $

# Drop old columns of pubSubNode
ALTER TABLE pubsubNode DROP COLUMN contacts;
ALTER TABLE pubsubNode DROP COLUMN rosterGroups;
ALTER TABLE pubsubNode DROP COLUMN replyRooms;
ALTER TABLE pubsubNode DROP COLUMN replyTo;
ALTER TABLE pubsubNode DROP COLUMN associationTrusted;

# Add new pubsub tables
CREATE TABLE pubsubNodeJIDs (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  jid                 VARCHAR(255)  NOT NULL,
  associationType     VARCHAR(20)   NOT NULL,
  PRIMARY KEY (serviceID, nodeID, jid(70))
);

CREATE TABLE pubsubNodeGroups (
  serviceID           VARCHAR(100)  NOT NULL,
  nodeID              VARCHAR(100)  NOT NULL,
  rosterGroup         VARCHAR(100)   NOT NULL,
  INDEX pubsubNodeGroups_idx (serviceID, nodeID)
);

UPDATE jiveVersion set version=8 where name = 'openfire';
