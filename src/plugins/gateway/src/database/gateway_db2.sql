CREATE TABLE gatewayRegistration (
   registrationID    BIGINT         NOT NULL,
   jid               VARCHAR(255)   NOT NULL,
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   password          VARCHAR(255),
   registrationDate  BIGINT         NOT NULL,
   lastLogin         BIGINT,
   CONSTRAINT gatewayReg_pk PRIMARY KEY (registrationID)
);
CREATE INDEX gatewayReg_jid_idx ON gatewayRegistration (jid);
CREATE INDEX gatewayReg_type_idx ON gatewayRegistration (transportType);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 0);
