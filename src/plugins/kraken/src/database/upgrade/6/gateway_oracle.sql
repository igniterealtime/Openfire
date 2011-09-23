-- Add imageType column to avatars table
ALTER TABLE gatewayAvatars ADD imageType VARCHAR2(25);

-- Update database version
UPDATE jiveVersion SET version = 6 WHERE name = 'gateway';

commit;