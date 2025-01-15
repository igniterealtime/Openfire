CREATE TABLE ofSpamReport (
    reportID INTEGER        NOT NULL,
    reporter VARCHAR2(1024) NOT NULL,
    reported VARCHAR2(1024) NOT NULL,
    reason   VARCHAR2(255)  NOT NULL,
    created  INTEGER        NOT NULL,
    context  CLOB           NULL,
    CONSTRAINT ofSpamReport_pk PRIMARY KEY (reportID)
);
CREATE INDEX ofSpamReport_created_reporter_id ON ofSpamReport (created, reporter);
CREATE INDEX ofSpamReport_created_reported_id ON ofSpamReport (created, reported);

CREATE TABLE ofSpamStanza (
    reportID      INTEGER        NOT NULL,
    stanzaIDValue VARCHAR2(1024) NOT NULL,
    stanzaIDBy    VARCHAR2(1024) NOT NULL,
    stanza        CLOB           NULL
);
CREATE INDEX ofSpamStanza_reportID ON ofSpamStanza (reportID);

INSERT INTO ofID (idType, id) VALUES (42, 1);

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';

COMMIT;
