INSERT INTO ofID (idType, id) VALUES (27, (SELECT coalesce(max(messageID), 1) FROM ofMucConversationLog) );

UPDATE ofVersion SET version = 30 WHERE name = 'openfire';
