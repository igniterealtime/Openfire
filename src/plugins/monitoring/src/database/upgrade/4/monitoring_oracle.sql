CREATE INDEX ofMessageArchive_fromjid_idx ON ofMessageArchive (fromJID);
CREATE INDEX ofMessageArchive_tojid_idx ON ofMessageArchive (toJID);
-- Update database version
UPDATE ofVersion SET version = 4 WHERE name = 'monitoring';

COMMIT;
