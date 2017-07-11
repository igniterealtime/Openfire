ALTER TABLE ofMucConversationLog ADD stanza NTEXT NULL;
ALTER TABLE ofMucConversationLog ADD messageID INT NULL;
UPDATE ofVersion SET version = 24 WHERE name = 'openfire';
