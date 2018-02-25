CREATE TABLE ofExternalServices (
    serviceID         INTEGER         NOT NULL,
    name              VARCHAR2(255),
    host              VARCHAR2(255)   NOT NULL,
    port              INT,
    restricted        SMALLINT,
    transport         CHAR(3),
    type              VARCHAR2(10)    NOT NULL,
    username          VARCHAR2(255),
    password          VARCHAR2(1024),
    sharedSecret      VARCHAR2(1024)
);

INSERT INTO ofID (idType, id) VALUES (937, 1);

INSERT INTO ofVersion (name, version) VALUES ('externalservicediscovery', 1);
