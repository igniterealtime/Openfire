CREATE TABLE ofGatewayRegistration (
   registrationID    INTEGER         NOT NULL,
   jid               VARCHAR2(255)   NOT NULL,
   transportType     VARCHAR2(15)    NOT NULL,
   username          VARCHAR2(255)   NOT NULL,
   password          VARCHAR2(255),
   nickname          VARCHAR2(255),
   registrationDate  INTEGER         NOT NULL,
   lastLogin         INTEGER,
   CONSTRAINT ofGatewayReg_pk PRIMARY KEY (registrationID)
);
CREATE INDEX ofGatewayReg_jid_idx ON ofGatewayRegistration (jid);
CREATE INDEX ofGatewayReg_type_idx ON ofGatewayRegistration (transportType);

CREATE TABLE ofGatewayPseudoRoster (
   registrationID    INTEGER        NOT NULL,
   username          VARCHAR2(255)  NOT NULL,
   nickname          VARCHAR2(255),
   groups            VARCHAR2(255)
);
CREATE INDEX ofGatewayPsdRstr_regid_idx ON ofGatewayPseudoRoster (registrationID);
CREATE INDEX ofGatewayPsdRstr_uname_idx ON ofGatewayPseudoRoster (username);

CREATE TABLE ofGatewayRestrictions (
   transportType     VARCHAR2(15)   NOT NULL,
   username          VARCHAR2(255),
   groupname         VARCHAR2(50)
);
CREATE INDEX ofGatewayRstrs_ttype_idx ON ofGatewayRestrictions (transportType);
CREATE INDEX ofGatewayRstrs_uname_idx ON ofGatewayRestrictions (username);

CREATE TABLE ofGatewayAvatars (
   jid               VARCHAR2(255)  NOT NULL,
   imageData         CLOB           NOT NULL,
   xmppHash          VARCHAR2(255),
   legacyIdentifier  VARCHAR2(255),
   createDate        INTEGER        NOT NULL,
   lastUpdate        INTEGER,
   imageType         VARCHAR2(25)
);
CREATE INDEX ofGatewayAvatars_jid_idx ON ofGatewayAvatars (jid);

CREATE TABLE ofGatewayVCards (
   jid               VARCHAR2(255)  NOT NULL,
   value             CLOB           NOT NULL
);
CREATE INDEX ofGatewayVCards_jid_idx ON ofGatewayVCards (jid);

INSERT INTO ofVersion (name, version) VALUES ('gateway', 11);

commit;
