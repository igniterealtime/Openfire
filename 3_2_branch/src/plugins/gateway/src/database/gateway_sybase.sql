CREATE TABLE gatewayRegistration (
   registrationID    INTEGER         NOT NULL,
   jid               NVARCHAR(255)   NOT NULL,
   transportType     NVARCHAR(15)    NOT NULL,
   username          NVARCHAR(255)   NOT NULL,
   password          NVARCHAR(255),
   nickname          NVARCHAR(255),
   registrationDate  INTEGER         NOT NULL,
   lastLogin         INTEGER,
   CONSTRAINT gatewayReg_pk PRIMARY KEY (registrationID)
);
CREATE INDEX gatewayReg_jid_idx ON gatewayRegistration (jid);
CREATE INDEX gatewayReg_type_idx ON gatewayRegistration (transportType);

CREATE TABLE gatewayPseudoRoster (
   registrationID    INTEGER         NOT NULL,
   username          NVARCHAR(255)   NOT NULL,
   nickname          NVARCHAR(255),
   groups            NVARCHAR(255)
);
CREATE INDEX gatewayPsRs_regid_idx ON gatewayPseudoRoster (registrationID);
CREATE INDEX gatewayPsRs_uname_idx ON gatewayPseudoRoster (username);

CREATE TABLE gatewayRestrictions (
   transportType     NVARCHAR(15)    NOT NULL,
   username          NVARCHAR(255),
   groupname         NVARCHAR(50)
);
CREATE INDEX gatewayRstr_ttype_idx ON gatewayRestrictions (transportType);
CREATE INDEX gatewayRstr_uname_idx ON gatewayRestrictions (username);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 2);
