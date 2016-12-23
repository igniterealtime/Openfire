ALTER TABLE ofMucConversationLog ADD COLUMN stanza NTEXT NULL;
ALTER TABLE ofMucConversationLog ADD COLUMN messageID INT NULL;
UPDATE ofVersion SET version = 24 WHERE name = 'openfire';