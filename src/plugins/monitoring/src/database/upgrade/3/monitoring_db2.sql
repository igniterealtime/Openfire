
ALTER TABLE ofMessageArchive ADD COLUMN messageID BIGINT NULL;
ALTER TABLE ofMessageArchive ADD COLUMN stanza LONG VARCHAR NULL;

-- Update database version
UPDATE ofVersion SET version = 3 WHERE name = 'monitoring';
