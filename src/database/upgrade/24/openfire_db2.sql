ALTER TABLE ofMucConversationLog ADD COLUMN stanza CLOB;
ALTER TABLE ofMucConversationLog ADD COLUMN messageID INTEGER;

UPDATE ofVersion SET version = 24 WHERE name = 'openfire';
