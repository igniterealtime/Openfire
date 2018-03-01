
ALTER TABLE ofMessageArchive ALTER COLUMN body NVARCHAR(MAX) NULL;

-- Update database version
UPDATE ofVersion SET version = 2 WHERE name = 'monitoring';
