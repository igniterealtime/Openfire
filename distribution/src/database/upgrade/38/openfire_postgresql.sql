CREATE TABLE ofSpamReport (
                              reporter VARCHAR(1024) NOT NULL,
                              reported VARCHAR(1024) NOT NULL,
                              reason   VARCHAR(255)  NOT NULL,
                              created  BIGINT        NOT NULL,
                              raw      TEXT          NOT NULL
);
CREATE INDEX ofSpamReport_created_reporter_id ON ofSpamReport (created, reporter);
CREATE INDEX ofSpamReport_created_reported_id ON ofSpamReport (created, reported);

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
