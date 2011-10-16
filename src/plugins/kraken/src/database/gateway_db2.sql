CREATE TABLE ofGatewayRegistration (
   registrationID    BIGINT         NOT NULL,
   jid               VARCHAR(255)   NOT NULL,
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   password          VARCHAR(1024),
   nickname          VARCHAR(255),
   registrationDate  BIGINT         NOT NULL,
   lastLogin         BIGINT,
   CONSTRAINT ofGatewayReg_pk PRIMARY KEY (registrationID)
);
CREATE INDEX ofGatewayReg_jid_idx ON ofGatewayRegistration (jid);
CREATE INDEX ofGatewayReg_type_idx ON ofGatewayRegistration (transportType);

CREATE TABLE ofGatewayPseudoRoster (
   registrationID    BIGINT         NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   nickname          VARCHAR(255),
   groups            VARCHAR(255)
);
CREATE INDEX ofGatewayPsdoRstr_regid_idx ON ofGatewayPseudoRoster (registrationID);
CREATE INDEX ofGatewayPsdoRstr_uname_idx ON ofGatewayPseudoRoster (username);

CREATE TABLE ofGatewayRestrictions (
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255),
   groupname         VARCHAR(50)
);
CREATE INDEX ofGatewayRstrs_ttype_idx ON ofGatewayRestrictions (transportType);
CREATE INDEX ofGatewayRstrs_uname_idx ON ofGatewayRestrictions (username);

CREATE TABLE ofGatewayAvatars (
   jid               VARCHAR(255)   NOT NULL,
   imageData         CLOB           NOT NULL,
   xmppHash          VARCHAR(255),
   legacyIdentifier  VARCHAR(255),
   createDate        BIGINT         NOT NULL,
   lastUpdate        BIGINT,
   imageType         VARCHAR(25)
);
CREATE INDEX ofGatewayAvtrs_jid_idx ON ofGatewayAvatars (jid);

CREATE TABLE ofGatewayVCards (
   jid               VARCHAR(255)   NOT NULL,
   value             CLOB           NOT NULL
);
CREATE INDEX ofGatewayVCards_jid_idx ON ofGatewayVCards (jid);

INSERT INTO ofVersion (name, version) VALUES ('gateway', 12);
