CREATE TABLE gatewayRegistration (
   registrationID    INTEGER         NOT NULL,
   jid               VARCHAR2(255)   NOT NULL,
   transportType     VARCHAR2(15)    NOT NULL,
   username          VARCHAR2(255)   NOT NULL,
   password          VARCHAR2(255),
   registrationDate  INTEGER         NOT NULL,
   lastLogin         INTEGER,
   CONSTRAINT gatewayReg_pk PRIMARY KEY (registrationID)
);
CREATE INDEX gatewayReg_jid_idx ON gatewayRegistration (jid);
CREATE INDEX gatewayReg_type_idx ON gatewayRegistration (transportType);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 0);

commit;