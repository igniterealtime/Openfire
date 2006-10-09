-- Add nickname column to registration table
ALTER TABLE gatewayRegistration ADD nickname VARCHAR2(255) NULL;

-- Add pseudo roster table
CREATE TABLE gatewayPseudoRoster (
   registrationID    INTEGER        NOT NULL,
   username          VARCHAR2(255)  NOT NULL,
   nickname          VARCHAR2(255),
   groups            VARCHAR2(255)
);
CREATE INDEX gatewayPsRs_regid_idx ON gatewayPseudoRoster (registrationID);
CREATE INDEX gatewayPsRs_uname_idx ON gatewayPseudoRoster (username);

-- Update database version
UPDATE jiveVersion SET version = 1 WHERE name = 'gateway';

commit;
