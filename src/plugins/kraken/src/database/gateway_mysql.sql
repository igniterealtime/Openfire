CREATE TABLE ofGatewayRegistration (
   registrationID    BIGINT         NOT NULL,
   jid               VARCHAR(255)   NOT NULL,
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   password          VARCHAR(255),
   nickname          VARCHAR(255),
   registrationDate  BIGINT         NOT NULL,
   lastLogin         BIGINT,
   PRIMARY KEY (registrationID),
   INDEX ofGatewayRegistration_jid_idx(jid),
   INDEX ofGatewayRegistration_type_idx(transportType)
);

CREATE TABLE ofGatewayPseudoRoster (
   registrationID    BIGINT         NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   nickname          VARCHAR(255),
   groups            VARCHAR(255),
   INDEX ofGatewayPseudoRoster_regid_idx(registrationID),
   INDEX ofGatewayPseudoRoster_uname_idx(username)
);

CREATE TABLE ofGatewayRestrictions (
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255),
   groupname         VARCHAR(50),
   INDEX ofGatewayRestrictions_ttype_idx(transportType),
   INDEX ofGatewayRestrictions_uname_idx(username)
);

CREATE TABLE ofGatewayAvatars (
   jid               VARCHAR(255)   NOT NULL,
   imageData         MEDIUMTEXT     NOT NULL,
   xmppHash          VARCHAR(255),
   legacyIdentifier  VARCHAR(255),
   createDate        BIGINT         NOT NULL,
   lastUpdate        BIGINT,
   imageType         VARCHAR(25),
   PRIMARY KEY (jid)
);

CREATE TABLE ofGatewayVCards (
   jid               VARCHAR(255)   NOT NULL,
   value             MEDIUMTEXT     NOT NULL,
   PRIMARY KEY (jid)
);

INSERT INTO ofVersion (name, version) VALUES ('gateway', 11);
