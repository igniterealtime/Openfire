# Add vcards table
CREATE TABLE gatewayVCards (
   jid               VARCHAR(255)   NOT NULL,
   value             MEDIUMTEXT     NOT NULL,
   PRIMARY KEY (jid)
);

# Update database version
UPDATE jiveVersion SET version = 4 WHERE name = 'gateway';
