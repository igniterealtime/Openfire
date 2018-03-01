ALTER TABLE ofMessageArchive ADD INDEX ofMessageArchive_fromjid_idx (fromJID);
ALTER TABLE ofMessageArchive ADD INDEX ofMessageArchive_tojid_idx (toJID);
-- Update database version
UPDATE ofVersion SET version = 4 WHERE name = 'monitoring';
