CREATE TABLE ofGatewayRegistration (
   registrationID    INTEGER        NOT NULL,
   jid               VARCHAR(255)   NOT NULL,
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   password          VARCHAR(1024),
   nickname          VARCHAR(255),
   registrationDate  CHAR(15)       NOT NULL,
   lastLogin         CHAR(15),
   CONSTRAINT ofGatewayRegistration_pk PRIMARY KEY (registrationID)
);
CREATE INDEX ofGatewayRegistration_jid_idx ON ofGatewayRegistration (jid);
CREATE INDEX ofGatewayRegistration_type_idx ON ofGatewayRegistration (transportType);

CREATE TABLE ofGatewayPseudoRoster (
   registrationID    INTEGER        NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   nickname          VARCHAR(255),
   groups            VARCHAR(255)
);
CREATE INDEX ofGatewayPseudoRoster_regid_idx ON ofGatewayPseudoRoster (registrationID);
CREATE INDEX ofGatewayPseudoRoster_uname_idx ON ofGatewayPseudoRoster (username);

CREATE TABLE ofGatewayRestrictions (
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255),
   groupname         VARCHAR(50)
);
CREATE INDEX ofGatewayRestrictions_ttype_idx ON ofGatewayRestrictions (transportType);
CREATE INDEX ofGatewayRestrictions_uname_idx ON ofGatewayRestrictions (username);

CREATE TABLE ofGatewayAvatars (
   jid               VARCHAR(255)   NOT NULL,
   imageData         TEXT           NOT NULL,
   xmppHash          VARCHAR(255),
   legacyIdentifier  VARCHAR(255),
   createDate        CHAR(15)       NOT NULL,
   lastUpdate        CHAR(15),
   imageType         VARCHAR(25)
);
CREATE INDEX ofGatewayAvatars_jid_idx ON ofGatewayAvatars (jid);

CREATE TABLE ofGatewayVCards (
   jid               VARCHAR(255)   NOT NULL,
   value             TEXT           NOT NULL
);
CREATE INDEX ofGatewayVCards_jid_idx ON ofGatewayVCards (jid);

INSERT INTO ofVersion (name, version) VALUES ('gateway', 12);
