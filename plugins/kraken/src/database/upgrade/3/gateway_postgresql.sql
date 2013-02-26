-- Add avatars table
CREATE TABLE gatewayAvatars (
   jid               VARCHAR(255)   NOT NULL,
   imageData         TEXT           NOT NULL,
   xmppHash          VARCHAR(255),
   legacyIdentifier  VARCHAR(255),
   createDate        BIGINT         NOT NULL,
   lastUpdate        BIGINT
);
CREATE INDEX gatewayAvtr_jid_idx ON gatewayAvatars (jid);

-- Update database version
UPDATE jiveVersion SET version = 3 WHERE name = 'gateway';
