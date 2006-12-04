CREATE TABLE gatewayRegistration (
   registrationID    INTEGER         NOT NULL,
   jid               VARCHAR2(255)   NOT NULL,
   transportType     VARCHAR2(15)    NOT NULL,
   username          VARCHAR2(255)   NOT NULL,
   password          VARCHAR2(255),
   nickname          VARCHAR2(255),
   registrationDate  INTEGER         NOT NULL,
   lastLogin         INTEGER,
   CONSTRAINT gatewayReg_pk PRIMARY KEY (registrationID)
);
CREATE INDEX gatewayReg_jid_idx ON gatewayRegistration (jid);
CREATE INDEX gatewayReg_type_idx ON gatewayRegistration (transportType);

CREATE TABLE gatewayPseudoRoster (
   registrationID    INTEGER        NOT NULL,
   username          VARCHAR2(255)  NOT NULL,
   nickname          VARCHAR2(255),
   groups            VARCHAR2(255)
);
CREATE INDEX gatewayPsRs_regid_idx ON gatewayPseudoRoster (registrationID);
CREATE INDEX gatewayPsRs_uname_idx ON gatewayPseudoRoster (username);

CREATE TABLE gatewayRestrictions (
   transportType     VARCHAR2(15)   NOT NULL,
   username          VARCHAR2(255),
   groupname         VARCHAR2(50)
);
CREATE INDEX gatewayRstr_ttype_idx ON gatewayRestrictions (transportType);
CREATE INDEX gatewayRstr_uname_idx ON gatewayRestrictions (username);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 2);

commit;
