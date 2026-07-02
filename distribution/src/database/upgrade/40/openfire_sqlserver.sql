-- Move SCRAM credentials from ofUser columns into a dedicated, mechanism-keyed table.
--
-- This second step (schema version 40) drops the old SCRAM columns from ofUser, now that version 39 has
-- created ofUserScram and copied the data across.

ALTER TABLE ofUser DROP COLUMN storedKey;
ALTER TABLE ofUser DROP COLUMN serverKey;
ALTER TABLE ofUser DROP COLUMN salt;
ALTER TABLE ofUser DROP COLUMN iterations;

UPDATE ofVersion SET version = 40 WHERE name = 'openfire';
