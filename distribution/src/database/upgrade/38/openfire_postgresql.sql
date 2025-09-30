-- OF-2500: Switch from string-based to timestamp-based representations of date/time values.

ALTER TABLE ofUser
    ALTER COLUMN creationDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(creationDate AS BIGINT) / 1e3),
    ALTER COLUMN modificationDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(modificationDate AS BIGINT) / 1e3);

ALTER TABLE ofUserFlag
    ALTER COLUMN startTime SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(startTime AS BIGINT) / 1e3),
    ALTER COLUMN endTime SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(endTime AS BIGINT) / 1e3);

ALTER TABLE ofOffline
    ALTER COLUMN creationDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(creationDate AS BIGINT) / 1e3);

ALTER TABLE ofPresence
    ALTER COLUMN offlineDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(offlineDate AS BIGINT) / 1e3);

ALTER TABLE ofMucRoom
    ALTER COLUMN creationDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(creationDate AS BIGINT) / 1e3),
    ALTER COLUMN modificationDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(modificationDate AS BIGINT) / 1e3),
    ALTER COLUMN lockedDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(lockedDate AS BIGINT) / 1e3),
    ALTER COLUMN emptyDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(emptyDate AS BIGINT) / 1e3);

ALTER TABLE ofMucConversationLog
    ALTER COLUMN logTime SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(logTime AS BIGINT) / 1e3);

ALTER TABLE ofPubsubNode
    ALTER COLUMN creationDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(creationDate AS BIGINT) / 1e3),
    ALTER COLUMN modificationDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(modificationDate AS BIGINT) / 1e3);

ALTER TABLE ofPubsubItem
    ALTER COLUMN creationDate SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(creationDate AS BIGINT) / 1e3);

ALTER TABLE ofPubsubSubscription
    ALTER COLUMN expire SET DATA TYPE TIMESTAMP
        USING to_timestamp(CAST(expire AS BIGINT) / 1e3);

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
