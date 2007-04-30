-- $Revision:  $
-- $Date:  $

-- Drop old columns of pubSubNode
ALTER TABLE pubsubNode DROP COLUMN contacts;
ALTER TABLE pubsubNode DROP COLUMN rosterGroups;
ALTER TABLE pubsubNode DROP COLUMN replyRooms;
ALTER TABLE pubsubNode DROP COLUMN replyTo;
ALTER TABLE pubsubNode DROP COLUMN associationTrusted;

-- Add new pubsub tables
CREATE TABLE pubsubNodeJIDs (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  jid                 VARCHAR2(1024) NOT NULL,
  associationType     VARCHAR2(20)   NOT NULL,
  CONSTRAINT pubsubJID_pk PRIMARY KEY (serviceID, nodeID, jid)
);

CREATE TABLE pubsubNodeGroups (
  serviceID           VARCHAR2(100)  NOT NULL,
  nodeID              VARCHAR2(100)  NOT NULL,
  rosterGroup         VARCHAR2(100)  NOT NULL
);
CREATE INDEX pubsubNodeGroups_idx ON pubsubNodeGroups (serviceID, nodeID);

UPDATE jiveVersion set version=8 where name = 'openfire';

commit;