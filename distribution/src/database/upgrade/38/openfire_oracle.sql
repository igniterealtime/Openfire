-- This copies the subject-defining stanza as stored in the message archive into the room's table, replacing any text
-- based (non-stanza) subject that was stored there.
-- In rare occasions (see OF-3131) the room can have a subject, while the history does not. In those cases, this script
-- leaves the (non-stanza) subject in the ofMucRoom table intact (this means that the column holds a mixture of plain
-- text and XMPP data).
-- Note that the stanzas in ofMucConversationLog typically do not contain a timestamp (although one is provided in a
-- separate column). If the subject that gets migrated to ofMucRoom is used as-is, the time of subject change is likely
-- lost (until the room's subject gets changed). This is deemed an acceptable loss.
ALTER TABLE ofMucRoom MODIFY subject VARCHAR2(4000) NULL;

MERGE INTO ofMucRoom r
USING (
    SELECT roomID, stanza
    FROM (
        SELECT roomID, stanza,
               ROW_NUMBER() OVER (PARTITION BY roomID ORDER BY logTime DESC) AS rn
        FROM ofMucConversationLog
        WHERE subject IS NOT NULL
    ) t
    WHERE rn = 1
) c
ON (r.roomID = c.roomID)
WHEN MATCHED THEN
  UPDATE SET r.subject = c.stanza;

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
