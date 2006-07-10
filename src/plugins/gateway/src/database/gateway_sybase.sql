CREATE TABLE gatewayRegistration (
   gwID NVARCHAR(50) NOT NULL,
   username NVARCHAR(64) NOT NULL,
   legacyAcct NVARCHAR(255) NOT NULL,
   legacyPwd NVARCHAR(255) NOT NULL,
   CONSTRAINT gatewayRegistration_pk PRIMARY KEY (gwID, username)
);

INSERT INTO jiveVersion (name, version) VALUES ('gateway', 0);
