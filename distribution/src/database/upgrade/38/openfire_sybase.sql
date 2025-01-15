CREATE TABLE ofSpamReport (
    reporter NVARCHAR(1024) NOT NULL,
    reported NVARCHAR(1024) NOT NULL,
    reason   NVARCHAR(255)  NOT NULL,
    created  INTEGER        NOT NULL,
    "raw"    LONG VARCHAR   NOT NULL
);
CREATE INDEX ofSpamReport_created_reporter_id ON ofSpamReport (created, reporter);
CREATE INDEX ofSpamReport_created_reported_id ON ofSpamReport (created, reported);

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
