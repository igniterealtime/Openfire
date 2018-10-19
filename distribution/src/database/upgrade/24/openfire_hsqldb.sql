ALTER TABLE ofMucConversationLog ADD COLUMN stanza LONGVARCHAR NULL;
ALTER TABLE ofMucConversationLog ADD COLUMN messageID BIGINT NULL;
UPDATE ofVersion SET version = 24 WHERE name = 'openfire';
