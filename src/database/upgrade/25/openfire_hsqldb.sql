DROP INDEX ofMucConvLog_msg_id IF EXISTS;

CREATE INDEX ofMucConvLog_msg_id ON ofMucConversationLog (messageID);

UPDATE ofVersion SET version = 25 WHERE name = 'openfire';
