CREATE INDEX ofMucConvLog_msg_id ON ofMucConversationLog (messageID);

UPDATE ofVersion SET version = 24 WHERE name = 'openfire';