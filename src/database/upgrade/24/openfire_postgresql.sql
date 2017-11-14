ALTER TABLE ofMucConversationLog ADD COLUMN stanza TEXT NULL;
ALTER TABLE ofMucConversationLog ADD COLUMN messageID INTEGER NULL;
UPDATE ofVersion SET version = 24 WHERE name = 'openfire';
