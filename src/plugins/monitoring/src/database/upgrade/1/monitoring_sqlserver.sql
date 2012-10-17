-- $Revision$
-- $Date$

ALTER TABLE ofMessageArchive ADD fromJIDResource NVARCHAR(255) NULL;
ALTER TABLE ofMessageArchive ADD toJIDResource NVARCHAR(255) NULL;

-- Update database version
UPDATE jiveVersion SET version = 1 WHERE name = 'monitoring';