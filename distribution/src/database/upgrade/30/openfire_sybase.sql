INSERT INTO ofID (idType, id) (SELECT 27, coalesce(max(messageID), 1) FROM ofMucConversationLog)

UPDATE ofVersion SET version = 30 WHERE name = 'openfire'
