CREATE TABLE userStatus (
    username        VARCHAR(64)         NOT NULL,
    resource        VARCHAR(64)         NOT NULL,
    online          TINYINT             NOT NULL,
    presence        CHAR(15),
    lastIpAddress   CHAR(15)            NOT NULL,
    lastLoginDate   CHAR(15)            NOT NULL,
    lastLogoffDate  CHAR(15),
    PRIMARY KEY pk_userStatus (username, resource)
);

CREATE TABLE userStatusHistory (
    historyID       BIGINT              NOT NULL,
    username        VARCHAR(64)         NOT NULL,
    resource        VARCHAR(64)         NOT NULL,
    lastIpAddress   CHAR(15)            NOT NULL,
    lastLoginDate   CHAR(15)            NOT NULL,
    lastLogoffDate  CHAR(15)            NOT NULL,
    PRIMARY KEY pk_userStatusHistory (historyID)
);

INSERT INTO ofVersion (name, version) VALUES ('user-status', 0);
