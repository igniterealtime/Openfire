-- Add avatars table
CREATE TABLE gatewayAvatars (
   jid               VARCHAR2(255)  NOT NULL,
   imageData         BLOB           NOT NULL,
   xmppHash          VARCHAR2(255),
   legacyIdentifier  VARCHAR2(255),
   createDate        INTEGER        NOT NULL,
   lastUpdate        INTEGER
);
CREATE INDEX gatewayAvtr_jid_idx ON gatewayAvatars (jid);

-- Update database version
UPDATE jiveVersion SET version = 3 WHERE name = 'gateway';

commit;
