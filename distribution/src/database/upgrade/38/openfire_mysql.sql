-- OF-2500: Switch from string-based to timestamp-based representations of date/time values.
ALTER TABLE ofUser
    ADD COLUMN creationDateNew TIMESTAMP WITH TIME ZONE,
    ADD COLUMN modificationDateNew TIMESTAMP;

UPDATE ofUser SET
    creationDateNew = FROM_UNIXTIME(CAST(creationDate AS UNSIGNED) / 1000.0),
    modificationDateNew = FROM_UNIXTIME(CAST(modificationDate AS UNSIGNED) / 1000.0);

ALTER TABLE ofUser
    DROP COLUMN creationDate,
    DROP COLUMN modificationDate;

ALTER TABLE ofUser
    RENAME COLUMN creationDateNew TO creationDate,
    RENAME COLUMN modificationDateNew TO modificationDate;



ALTER TABLE ofUserFlag
    ADD COLUMN startTimeNew TIMESTAMP,  
    ADD COLUMN endTimeNew TIMESTAMP;

UPDATE ofUserFlag SET
    startTimeNew = FROM_UNIXTIME(CAST(startTime AS UNSIGNED) / 1000.0),
    endTimeNew = FROM_UNIXTIME(CAST(endTime AS UNSIGNED) / 1000.0);

ALTER TABLE ofUserFlag
    DROP COLUMN startTime,
    DROP COLUMN endTime;

ALTER TABLE ofUserFlag
    RENAME COLUMN startTimeNew TO startTime,
    RENAME COLUMN endTimeNew TO endTime;



ALTER TABLE ofOffline
    ADD COLUMN creationDateNew TIMESTAMP

UPDATE ofOffline SET
    creationDateNew = FROM_UNIXTIME(CAST(creationDate AS UNSIGNED) / 1000.0);

ALTER TABLE ofOffline
    DROP COLUMN creationDate;

ALTER TABLE ofOffline
    RENAME COLUMN creationDateNew TO creationDate;



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
