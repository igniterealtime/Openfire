// Add nickname to registration table
ALTER TABLE gatewayRegistration ADD COLUMN nickname VARCHAR(255) BEFORE registrationDate;

// Add pseudo roster table
CREATE TABLE gatewayPseudoRoster (
   registrationID    BIGINT         NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   nickname          VARCHAR(255),
   groups            VARCHAR(255)
);
CREATE INDEX gatewayPsRs_regid_idx ON gatewayPseudoRoster (registrationID);
CREATE INDEX gatewayPsRs_uname_idx ON gatewayPseudoRoster (username);

// Update database version
UPDATE jiveVersion SET version = 1 WHERE name = 'gateway';
