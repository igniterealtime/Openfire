TRUNCATE TABLE ofConversation;
INSERT INTO ofConversation
(conversationID, room, isExternal, startDate, lastActivity, messageCount)
SELECT conversationID, room, isExternal, startDate, lastActivity, messageCount
FROM entConversation;

TRUNCATE TABLE ofConParticipant;
INSERT INTO ofConParticipant
(conversationID, joinedDate, leftDate, bareJID, jidResource, nickname)
SELECT conversationID, joinedDate, leftDate, bareJID, jidResource, nickname
FROM entConParticipant;

TRUNCATE TABLE ofMessageArchive;
INSERT INTO ofMessageArchive
(conversationID, fromJID, toJID, sentDate, body)
SELECT conversationID, fromJID, toJID, sentDate, body
FROM entMessageArchive;

TRUNCATE TABLE ofRRDs;
INSERT INTO ofRRDs
(id, updatedDate, bytes)
SELECT id, updatedDate, bytes
FROM entRRDs;

