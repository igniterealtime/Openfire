-- This copies the subject-defining stanza as stored in the message archive into the room's table, replacing any text
-- based (non-stanza) subject that was stored there.
-- In rare occasions (see OF-3131) the room can have a subject, while the history does not. In those cases, this script
-- leaves the (non-stanza) subject in the ofMucRoom table intact (this means that the column holds a mixture of plain
-- text and XMPP data).
-- Note that the stanzas in ofMucConversationLog typically do not contain a timestamp (although one is provided in a
-- separate column). If the subject that gets migrated to ofMucRoom is used as-is, the time of subject change is likely
-- lost (until the room's subject gets changed). This is deemed an acceptable loss.

-- TEMPORARY TEST DATA (should not be released). This data is added only to verify that the migration that occurs later in this script is successful.
INSERT INTO ofMucRoom
(serviceID, roomID, creationDate, modificationDate, name, naturalName, lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, canInvite, roomPassword, canDiscoverJID, logEnabled, preserveHistOnDel, retireOnDeletion, subject, rolesToBroadcast, useReservedNick, canChangeNick, canRegister, allowpm, fmucEnabled, fmucOutboundNode, fmucOutboundMode, fmucInboundNodes)
VALUES
    (1, 1, '001759384723928', '001759384723928', 'test-room1', 'test-room1', '001759384723928' , '001759384723928', 1, 99, 1, 0, 0, 1, null, 0, 1, 1, 1, 'test subject1', 0, 0, 1, 1, 0, 0, null, null, null),
    (1, 2, '001759384723928', '001759384723928', 'test-room2', 'test-room2', '001759384723928' , '001759384723928', 1, 99, 1, 0, 0, 1, null, 0, 1, 1, 1, null, 0, 0, 1, 1, 0, 0, null, null, null),
    (1, 3, '001759384723928', '001759384723928', 'test-room3', 'test-room3', '001759384723928' , '001759384723928', 1, 99, 1, 0, 0, 1, null, 0, 1, 1, 1, 'test subject3', 0, 0, 1, 1, 0, 0, null, null, null),
    (1, 4, '001759384723928', '001759384723928', 'test-room4', 'test-room4', '001759384723928' , '001759384723928', 1, 99, 1, 0, 0, 1, null, 0, 1, 1, 1, null, 0, 0, 1, 1, 0, 0, null, null, null);

INSERT INTO ofMucConversationLog
(roomID, messageID, sender, nickname, logTime, subject, body, stanza)
VALUES
    (1, 1, 'testuser@example.org/someresource', 'Nickname', '001759384723927', null, 'a non-subject message 1', '<message type="groupchat" to="test-room1@conference.example.org" from="testuser@example.org/someresource"><body>a non-subject message</body><stanza-id xmlns="urn:xmpp:sid:0" id="1" by="test-room1@conference.example.org"/><occupant-id xmlns="urn:xmpp:occupant-id:0" id="1"/></message>'),
    (1, 2, 'testuser@example.org/someresource', 'Nickname', '001759384723928', 'test history subject 1', null, '<message type="groupchat" to="test-room1@conference.example.org" from="testuser@example.org/someresource"><subject>test history subject 1</subject><stanza-id xmlns="urn:xmpp:sid:0" id="1" by="test-room1@conference.example.org"/><occupant-id xmlns="urn:xmpp:occupant-id:0" id="1"/></message>'),
    (2, 3, 'testuser@example.org/someresource', 'Nickname', '001759384723928', 'test history subject 2', null, '<message type="groupchat" to="test-room2@conference.example.org" from="testuser@example.org/someresource"><subject>test history subject 2</subject><stanza-id xmlns="urn:xmpp:sid:0" id="2" by="test-room1@conference.example.org"/><occupant-id xmlns="urn:xmpp:occupant-id:0" id="2"/></message>'),
    (2, 4, 'testuser@example.org/someresource', 'Nickname', '001759384723929', null, 'a non-subject message 2', '<message type="groupchat" to="test-room2@conference.example.org" from="testuser@example.org/someresource"><body>a non-subject message 2</body><stanza-id xmlns="urn:xmpp:sid:0" id="2" by="test-room1@conference.example.org"/><occupant-id xmlns="urn:xmpp:occupant-id:0" id="2"/></message>'),
    (4, 5, 'testuser@example.org/someresource', 'Nickname', '001759384723922', null, 'a non-subject message 4', '<message type="groupchat" to="test-room4@conference.example.org" from="testuser@example.org/someresource"><body>a non-subject message 4</body><stanza-id xmlns="urn:xmpp:sid:0" id="2" by="test-room1@conference.example.org"/><occupant-id xmlns="urn:xmpp:occupant-id:0" id="2"/></message>');
-- END OF TEMPORARY TEST DATA


ALTER TABLE ofMucRoom ALTER COLUMN subject SET DATA TYPE LONGVARCHAR;

UPDATE ofMucRoom r
SET r.subject = (
    SELECT l.stanza
    FROM ofMucConversationLog l
    WHERE l.roomID = r.roomID
      AND l.subject IS NOT NULL
      AND l.logTime = (
          SELECT MAX(l2.logTime)
          FROM ofMucConversationLog l2
          WHERE l2.roomID = r.roomID
            AND l2.subject IS NOT NULL
      )
);

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
