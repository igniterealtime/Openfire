CREATE TABLE ofExternalServices (
    serviceID         INTEGER         NOT NULL,
    name              NVARCHAR(255),
    host              NVARCHAR(255)   NOT NULL,
    port              INT,
    restricted        BIT,
    transport         NCHAR(3),
    type              NVARCHAR(10)    NOT NULL,
    username          NVARCHAR(255),
    password          NVARCHAR(1024),
    sharedSecret      NVARCHAR(1024)
);

INSERT INTO ofID (idType, id) VALUES (937, 1);

INSERT INTO ofVersion (name, version) VALUES ('externalservicediscovery', 1);
