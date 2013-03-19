/* Add vcards table */
CREATE TABLE gatewayVCards (
   jid               NVARCHAR(255)  NOT NULL,
   value             TEXT           NOT NULL
);
CREATE INDEX gatewayVCrd_jid_idx ON gatewayVCards (jid);

/* Update database version */
UPDATE jiveVersion SET version = 4 WHERE name = 'gateway';
