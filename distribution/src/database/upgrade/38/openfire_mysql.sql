CREATE TABLE ofSpamReport (
    reportID     BIGINT        NOT NULL,
    reporter     VARCHAR(1024) NOT NULL,
    reported     VARCHAR(1024) NOT NULL,
    reason       VARCHAR(255)  NOT NULL,
    reportOrigin TINYINT       NOT NULL,
    thirdParty   TINYINT       NOT NULL,
    created      BIGINT        NOT NULL,
    context      TEXT          NULL,
    PRIMARY KEY (reportID),
    INDEX ofSpamReport_created_reporter_id (created, reporter),
    INDEX ofSpamReport_created_reported_id (created, reported)
);

CREATE TABLE ofSpamStanza (
    reportID      BIGINT        NOT NULL,
    stanzaIDValue VARCHAR(1024) NOT NULL,
    stanzaIDBy    VARCHAR(1024) NOT NULL,
    stanza        TEXT          NULL,
    INDEX ofSpamStanza_reportID (reportID)
);

INSERT INTO ofID (idType, id) VALUES (42, 1);

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
