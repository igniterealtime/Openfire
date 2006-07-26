CREATE TABLE gatewayRegistration (
   registrationID    BIGINT         NOT NULL,
   jid               VARCHAR(1024)  NOT NULL,
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255)   NOT NULL,
   password          VARCHAR(255),
   registrationDate  BIGINT         NOT NULL,
   lastLogin         BIGINT,
   PRIMARY KEY (registrationID),
   INDEX gatewayReg_jid_idx(jid(500)),
   INDEX gatewayReg_type_idx(transportType)
);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 0);
