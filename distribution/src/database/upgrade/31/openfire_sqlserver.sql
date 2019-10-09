CREATE TABLE ofUserContactAddress (
  cID                     INTEGER       NOT NULL,
  cType                   NVARCHAR(20)   NOT NULL,
  cAddressType            NVARCHAR(20)   NOT NULL,
  cAddress                NVARCHAR(100)  NOT NULL,
  cDesciption             NVARCHAR(3000) ,
  CONSTRAINT ofContact_pk PRIMARY KEY (cID)
);

UPDATE ofVersion SET version = 31 WHERE name = 'openfire';