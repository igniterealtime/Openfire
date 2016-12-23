ALTER TABLE ofMucConversationLog ADD COLUMN stanza VARCHAR2(4000) NULL;
ALTER TABLE ofMucConversationLog ADD COLUMN messageID INT NULL;
UPDATE ofVersion SET version = 24 WHERE name = 'openfire';