-- This copies the subject-defining stanza as stored in the message archive into the room's table, replacing any text
-- based (non-stanza) subject that was stored there.
-- In rare occasions (see OF-3131) the room can have a subject, while the history does not. In those cases, this script
-- leaves the (non-stanza) subject in the ofMucRoom table intact (this means that the column holds a mixture of plain
-- text and XMPP data).
-- Note that the stanzas in ofMucConversationLog typically do not contain a timestamp (although one is provided in a
-- separate column). If the subject that gets migrated to ofMucRoom is used as-is, the time of subject change is likely
-- lost (until the room's subject gets changed). This is deemed an acceptable loss.
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
