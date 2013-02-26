/* Add avatars table */
CREATE TABLE gatewayAvatars (
   jid               NVARCHAR(255)  NOT NULL,
   imageData         NTEXT          NOT NULL,
   xmppHash          NVARCHAR(255),
   legacyIdentifier  NVARCHAR(255),
   createDate        BIGINT         NOT NULL,
   lastUpdate        BIGINT
);
CREATE INDEX gatewayAvtr_jid_idx ON gatewayAvatars (jid);

/* Update database version */
UPDATE jiveVersion SET version = 3 WHERE name = 'gateway';
