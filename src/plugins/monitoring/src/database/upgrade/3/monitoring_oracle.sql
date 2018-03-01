
ALTER TABLE ofMessageArchive ADD messageID INTEGER NULL;
ALTER TABLE ofMessageArchive ADD stanza LONG NULL;

-- Update database version
UPDATE ofVersion SET version = 3 WHERE name = 'monitoring';

commit;
