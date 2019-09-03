CREATE TABLE ofUserContactAddress (
  cID                     BIGINT       NOT NULL,
  cType                   VARCHAR(20)   NOT NULL,
  cAddressType            VARCHAR(20)   NOT NULL,
  cAddress                VARCHAR(100)  NOT NULL,
  cDesciption             VARCHAR(3000) NOT NULL,
  PRIMARY KEY (cID)
);

UPDATE ofVersion SET version = 31 WHERE name = 'openfire';