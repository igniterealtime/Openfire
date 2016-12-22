ALTER TABLE ofMucConversationLog ADD INDEX ofMucConvLog_msg_id (messageID);

UPDATE ofVersion SET version = 25 WHERE name = 'openfire';