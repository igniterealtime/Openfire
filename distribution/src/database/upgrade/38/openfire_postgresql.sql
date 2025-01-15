CREATE TABLE ofSpamReport (
    reportID BIGINT        NOT NULL,
    reporter VARCHAR(1024) NOT NULL,
    reported VARCHAR(1024) NOT NULL,
    reason   VARCHAR(255)  NOT NULL,
    created  BIGINT        NOT NULL,
    context  TEXT          NULL,
    CONSTRAINT ofSpamReport_pk PRIMARY KEY (reportID)
);
CREATE INDEX ofSpamReport_created_reporter_id ON ofSpamReport (created, reporter);
CREATE INDEX ofSpamReport_created_reported_id ON ofSpamReport (created, reported);

CREATE TABLE ofSpamStanza (
  reportID      BIGINT        NOT NULL,
  stanzaIDValue VARCHAR(1024) NOT NULL,
  stanzaIDBy    VARCHAR(1024) NOT NULL,
  stanza        TEXT          NULL
);
CREATE INDEX ofSpamStanza_reportID ON ofSpamStanza (reportID);

INSERT INTO ofID (idType, id) VALUES (42, 1);

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
