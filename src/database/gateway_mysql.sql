CREATE TABLE gatewayRegistration (
   regisrationID     BIGINT         NOT NULL,
   jid               TEXT           NOT NULL,
   gatewayType       VARCHAR(15)    NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   password          VARCHAR(255),
   registrationDate  BIGINT         NOT NULL,
   lastLogin         BIGINT,
   PRIMARY KEY (registrationID),
   INDEX gatewayReg_jid_idx(jid),
   INDEX gatewayReg_type_idx(gatewayType)
);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 0);