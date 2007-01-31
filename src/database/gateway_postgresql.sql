CREATE TABLE gatewayRegistration (
   registrationID    BIGINT         NOT NULL,
   jid               VARCHAR(255)   NOT NULL,
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   password          VARCHAR(255),
   nickname          VARCHAR(255),
   registrationDate  BIGINT         NOT NULL,
   lastLogin         BIGINT,
   CONSTRAINT gatewayReg_pk PRIMARY KEY (registrationID)
);
CREATE INDEX gatewayReg_jid_idx ON gatewayRegistration (jid);
CREATE INDEX gatewayReg_type_idx ON gatewayRegistration (transportType);

CREATE TABLE gatewayPseudoRoster (
   registrationID    BIGINT         NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   nickname          VARCHAR(255),
   groups            VARCHAR(255)
);
CREATE INDEX gatewayPsRs_regid_idx ON gatewayPseudoRoster (registrationID);
CREATE INDEX gatewayPsRs_uname_idx ON gatewayPseudoRoster (username);

CREATE TABLE gatewayRestrictions (
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255),
   groupname         VARCHAR(50)
);
CREATE INDEX gatewayRstr_ttype_idx ON gatewayRestrictions (transportType);
CREATE INDEX gatewayRstr_uname_idx ON gatewayRestrictions (username);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 2);
