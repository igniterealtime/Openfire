-- $Revision$
-- $Date$

ALTER TABLE ofMessageArchive ADD COLUMN fromJIDResource VARCHAR(255) NULL;
ALTER TABLE ofMessageArchive ADD COLUMN toJIDResource VARCHAR(255) NULL;

-- Update database version
UPDATE ofVersion SET version = 1 WHERE name = 'monitoring';