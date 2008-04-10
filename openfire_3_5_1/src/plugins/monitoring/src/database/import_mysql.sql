INSERT INTO ofConversation
(conversationID, room, isExternal, startDate, lastActivity, messageCount)
SELECT conversationID, room, isExternal, startDate, lastActivity, messageCount
FROM entConversation;

INSERT INTO ofConParticipant
(conversationID, joinedDate, leftDate, bareJID, jidResource, nickname)
SELECT conversationID, joinedDate, leftDate, bareJID, jidResource, nickname
FROM entConParticipant;

INSERT INTO ofMessageArchive
(conversationID, fromJID, toJID, sentDate, body)
SELECT conversationID, fromJID, toJID, sentDate, body
FROM entMessageArchive;

INSERT INTO ofRRDs
(id, updatedDate, bytes)
SELECT id, updatedDate, bytes
FROM entRRDs;

