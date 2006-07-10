CREATE TABLE gatewayRegistration (
   gwID VARCHAR2(50) NOT NULL,
   username VARCHAR2(64) NOT NULL,
   legacyAcct VARCHAR2(255) NOT NULL,
   legacyPwd VARCHAR2(255) NOT NULL,
   CONSTRAINT gatewayRegistration_pk PRIMARY KEY (gwID, username)
);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 0);
