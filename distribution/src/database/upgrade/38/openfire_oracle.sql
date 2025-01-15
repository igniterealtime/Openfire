CREATE TABLE ofSpamReport (
                              reporter VARCHAR2(1024) NOT NULL,
                              reported VARCHAR2(1024) NOT NULL,
                              reason   VARCHAR2(255)  NOT NULL,
                              created  INTEGER        NOT NULL,
                              "raw"    CLOB           NOT NULL
);
CREATE INDEX ofSpamReport_created_reporter_id ON ofSpamReport (created, reporter);
CREATE INDEX ofSpamReport_created_reported_id ON ofSpamReport (created, reported);

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';

COMMIT;
