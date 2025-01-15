CREATE TABLE ofSpamReport (
                              reporter VARCHAR(1024) NOT NULL,
                              reported VARCHAR(1024) NOT NULL,
                              reason   VARCHAR(255)  NOT NULL,
                              created  BIGINT        NOT NULL,
                              raw      TEXT          NOT NULL,
                              INDEX ofSpamReport_created_reporter_id (created, reporter),
                              INDEX ofSpamReport_created_reported_id (created, reported)
);

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
