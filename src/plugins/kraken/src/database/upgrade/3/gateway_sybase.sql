/* Add avatars table */
CREATE TABLE gatewayAvatars (
   jid               NVARCHAR(255)  NOT NULL,
   imageData         TEXT           NOT NULL,
   xmppHash          NVARCHAR(255),
   legacyIdentifier  NVARCHAR(255),
   createDate        INTEGER        NOT NULL,
   lastUpdate        INTEGER
);
CREATE INDEX gatewayAvtr_jid_idx ON gatewayAvatars (jid);

/* Update database version */
UPDATE jiveVersion SET version = 3 WHERE name = 'gateway';
