
ALTER TABLE ofMessageArchive ADD messageID BIGINT NULL;
ALTER TABLE ofMessageArchive ADD stanza NVARCHAR(MAX) NULL;

-- Update database version
UPDATE ofVersion SET version = 3 WHERE name = 'monitoring';
