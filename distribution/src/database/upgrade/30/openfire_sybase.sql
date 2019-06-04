INSERT INTO ofID (idType, id) VALUES (27, (SELECT max(messageID) FROM ofMucConversationLog) );

UPDATE ofVersion SET version = 30 WHERE name = 'openfire';
