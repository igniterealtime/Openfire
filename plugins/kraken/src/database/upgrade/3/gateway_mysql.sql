# Add avatars table
CREATE TABLE gatewayAvatars (
   jid               VARCHAR(255)   NOT NULL,
   imageData         MEDIUMTEXT     NOT NULL,
   xmppHash          VARCHAR(255),
   legacyIdentifier  VARCHAR(255),
   createDate        BIGINT         NOT NULL,
   lastUpdate        BIGINT,
   PRIMARY KEY (jid),
   INDEX gatewayAvtr_jid_idx(jid)
);

# Update database version
UPDATE jiveVersion SET version = 3 WHERE name = 'gateway';
