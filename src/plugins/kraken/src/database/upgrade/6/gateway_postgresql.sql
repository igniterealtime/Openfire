-- Add imageType column to avatars table
ALTER TABLE gatewayAvatars ADD COLUMN imageType VARCHAR(25);

-- Update database version
UPDATE jiveVersion SET version = 6 WHERE name = 'gateway';
