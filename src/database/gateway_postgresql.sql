CREATE TABLE gatewayRegistration (
   gwID VARCHAR(50) NOT NULL,
   username VARCHAR(64) NOT NULL,
   legacyAcct VARCHAR(255) NOT NULL,
   legacyPwd VARCHAR(255) NOT NULL,
   CONSTRAINT gatewayRegistration_pk PRIMARY KEY (gwID, username)
);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 0);
