CREATE TABLE gatewayRegistration (
   registrationID    BIGINT         NOT NULL,
   jid               VARCHAR(255)   NOT NULL,
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   password          VARCHAR(255),
   nickname          VARCHAR(255),
   registrationDate  BIGINT         NOT NULL,
   lastLogin         BIGINT,
   PRIMARY KEY (registrationID),
   INDEX gatewayReg_jid_idx(jid),
   INDEX gatewayReg_type_idx(transportType)
);

CREATE TABLE gatewayPseudoRoster (
   registrationID    BIGINT         NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   nickname          VARCHAR(255),
   groups            VARCHAR(255),
   INDEX gatewayPsRs_regid_idx(registrationID),
   INDEX gatewayPsRs_uname_idx(username)
);

CREATE TABLE gatewayRestrictions (
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255),
   groupname         VARCHAR(50),
   INDEX gatewayRstr_ttype_idx(transportType),
   INDEX gatewayRstr_uname_idx(username)
);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 2);
