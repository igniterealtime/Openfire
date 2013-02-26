-- Add vcards table
CREATE TABLE gatewayVCards (
   jid               VARCHAR(255)   NOT NULL,
   value             BLOB           NOT NULL
);
CREATE INDEX gatewayVCrd_jid_idx ON gatewayVCards (jid);

-- Update database version
UPDATE jiveVersion SET version = 4 WHERE name = 'gateway';
