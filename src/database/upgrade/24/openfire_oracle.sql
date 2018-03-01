ALTER TABLE ofMucConversationLog ADD stanza VARCHAR2(4000) NULL;
ALTER TABLE ofMucConversationLog ADD messageID INT NULL;
UPDATE ofVersion SET version = 24 WHERE name = 'openfire';
