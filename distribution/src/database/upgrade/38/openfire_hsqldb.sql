CREATE TABLE ofSpamReport (
    reportID     BIGINT        NOT NULL,
    reporter     VARCHAR(1024) NOT NULL,
    reported     VARCHAR(1024) NOT NULL,
    reason       VARCHAR(255)  NOT NULL,
    reportOrigin INTEGER       NOT NULL,
    thirdParty   INTEGER       NOT NULL,
    created      BIGINT        NOT NULL,
    context      LONGVARCHAR   NULL,
    CONSTRAINT ofSpamReport PRIMARY KEY (reportID)
);
CREATE INDEX ofSpamReport_created_reporter_id ON ofSpamReport (created, reporter);
CREATE INDEX ofSpamReport_created_reported_id ON ofSpamReport (created, reported);

CREATE TABLE ofSpamStanza (
    reportID      BIGINT        NOT NULL,
    stanzaIDValue VARCHAR(1024) NOT NULL,
    stanzaIDBy    VARCHAR(1024) NOT NULL,
    stanza        LONGVARCHAR   NULL
);
CREATE INDEX ofSpamStanza_reportID ON ofSpamStanza (reportID);

INSERT INTO ofID (idType, id) VALUES (42, 1);

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
