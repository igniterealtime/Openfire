-- This copies the subject-defining stanza as stored in the message archive into the room's table, replacing any text
-- based (non-stanza) subject that was stored there.
-- In rare occasions (see OF-3131) the room can have a subject, while the history does not. In those cases, this script
-- leaves the (non-stanza) subject in the ofMucRoom table intact (this means that the column holds a mixture of plain
-- text and XMPP data).
-- Note that the stanzas in ofMucConversationLog typically do not contain a timestamp (although one is provided in a
-- separate column). If the subject that gets migrated to ofMucRoom is used as-is, the time of subject change is likely
-- lost (until the room's subject gets changed). This is deemed an acceptable loss.
ALTER TABLE ofMucRoom ALTER COLUMN subject SET DATA TYPE LONGVARCHAR;

CREATE INDEX ofMucConversationLog_room_subject_time_idx
    ON ofMucConversationLog(roomID, subject, logTime);

UPDATE ofMucRoom r
SET subject = (
    SELECT l1.stanza
    FROM ofMucConversationLog l1
    JOIN (
        SELECT roomID, MAX(logTime) AS maxTime
        FROM ofMucConversationLog
        WHERE subject IS NOT NULL
        GROUP BY roomID
    ) lm
    ON lm.roomID = l1.roomID
    AND lm.maxTime = l1.logTime
    WHERE l1.roomID = r.roomID
      AND l1.subject IS NOT NULL
      AND l1.stanza IS NOT NULL
);

DROP INDEX ofMucConversationLog_room_subject_time_idx;

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
