# Add nickname to registration table
ALTER TABLE gatewayRegistration ADD COLUMN nickname VARCHAR(255) NULL AFTER password;

# Add pseudo roster table
CREATE TABLE gatewayPseudoRoster (
   registrationID    BIGINT         NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   nickname          VARCHAR(255),
   groups            VARCHAR(255),
   INDEX gatewayPsRs_regid_idx(registrationID),
   INDEX gatewayPsRs_uname_idx(username)
);

# Update database version
UPDATE jiveVersion SET version = 1 WHERE name = 'gateway';
