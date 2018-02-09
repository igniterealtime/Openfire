CREATE TABLE ofExternalServices (
    serviceID         BIGINT          NOT NULL,
    name              VARCHAR(255),
    host              VARCHAR(255)    NOT NULL,
    port              INT,
    restricted        BOOLEAN,
    transport         CHAR(3),
    type              VARCHAR(10)     NOT NULL,
    username          VARCHAR(255),
    password          VARCHAR(1024),
    sharedSecret      VARCHAR(1024)
);

INSERT INTO ofID (idType, id) VALUES (937, 1);

INSERT INTO ofVersion (name, version) VALUES ('externalservicediscovery', 1);
