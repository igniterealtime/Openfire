/* Add nickname column to registration table */
ALTER TABLE gatewayRegistration ADD nickname NVARCHAR(255);

/* Add pseudo roster table */
CREATE TABLE gatewayPseudoRoster (
   registrationID    INTEGER         NOT NULL,
   username          NVARCHAR(255)   NOT NULL,
   nickname          NVARCHAR(255),
   groups            NVARCHAR(255)
);
CREATE INDEX gatewayPsRs_regid_idx ON gatewayPseudoRoster (registrationID);
CREATE INDEX gatewayPsRs_uname_idx ON gatewayPseudoRoster (username);

/* Update database version */
UPDATE jiveVersion SET version = 1 WHERE name = 'gateway';
