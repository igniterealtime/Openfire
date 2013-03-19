-- $Revision$
-- $Date$

ALTER TABLE ofMessageArchive ADD fromJIDResource VARCHAR(255) NULL;
ALTER TABLE ofMessageArchive ADD toJIDResource VARCHAR(255) NULL;

-- Update database version
UPDATE jiveVersion SET version = 1 WHERE name = 'monitoring';

commit;