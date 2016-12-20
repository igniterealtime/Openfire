ALTER TABLE ofMucConversationLog ADD INDEX ofMucConvLog_msg_id (messageID);

UPDATE ofVersion SET version = 24 WHERE name = 'openfire';