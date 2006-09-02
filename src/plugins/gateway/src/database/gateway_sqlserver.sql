CREATE TABLE gatewayRegistration (
   registrationID    INTEGER         NOT NULL,
   jid               NVARCHAR(255)   NOT NULL,
   transportType     NVARCHAR(15)    NOT NULL,
   username          NVARCHAR(255)   NOT NULL,
   password          NVARCHAR(255),
   registrationDate  INTEGER         NOT NULL,
   lastLogin         INTEGER,
   CONSTRAINT gatewayReg_pk PRIMARY KEY (registrationID)
);
CREATE INDEX gatewayReg_jid_idx ON gatewayRegistration (jid);
CREATE INDEX gatewayReg_type_idx ON gatewayRegistration (transportType);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 0);
