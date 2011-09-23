CREATE TABLE ofGatewayRegistration (
   registrationID    INTEGER         NOT NULL,
   jid               NVARCHAR(255)   NOT NULL,
   transportType     NVARCHAR(15)    NOT NULL,
   username          NVARCHAR(255)   NOT NULL,
   password          NVARCHAR(255),
   nickname          NVARCHAR(255),
   registrationDate  INTEGER         NOT NULL,
   lastLogin         INTEGER,
   CONSTRAINT ofGatewayRegistration_pk PRIMARY KEY (registrationID)
);
CREATE INDEX ofGatewayRegistration_jid_idx ON ofGatewayRegistration (jid);
CREATE INDEX ofGatewayRegistration_type_idx ON ofGatewayRegistration (transportType);

CREATE TABLE ofGatewayPseudoRoster (
   registrationID    INTEGER         NOT NULL,
   username          NVARCHAR(255)   NOT NULL,
   nickname          NVARCHAR(255),
   groups            NVARCHAR(255)
);
CREATE INDEX ofGatewayPseudoRoster_regid_idx ON ofGatewayPseudoRoster (registrationID);
CREATE INDEX ofGatewayPseudoRoster_uname_idx ON ofGatewayPseudoRoster (username);

CREATE TABLE ofGatewayRestrictions (
   transportType     NVARCHAR(15)    NOT NULL,
   username          NVARCHAR(255),
   groupname         NVARCHAR(50)
);
CREATE INDEX ofGatewayRestrictions_ttype_idx ON ofGatewayRestrictions (transportType);
CREATE INDEX ofGatewayRestrictions_uname_idx ON ofGatewayRestrictions (username);

CREATE TABLE ofGatewayAvatars (
   jid               NVARCHAR(255)  NOT NULL,
   imageData         TEXT           NOT NULL,
   xmppHash          NVARCHAR(255),
   legacyIdentifier  NVARCHAR(255),
   createDate        INTEGER        NOT NULL,
   lastUpdate        INTEGER,
   imageType         NVARCHAR(25)
);
CREATE INDEX ofGatewayAvatars_jid_idx ON ofGatewayAvatars (jid);

CREATE TABLE ofGatewayVCards (
   jid               NVARCHAR(255)  NOT NULL,
   value             TEXT           NOT NULL
);
CREATE INDEX ofGatewayVCards_jid_idx ON ofGatewayVCards (jid);

INSERT INTO ofVersion (name, version) VALUES ('gateway', 11);
