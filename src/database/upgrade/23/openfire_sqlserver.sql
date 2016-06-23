ALTER TABLE ofMucRoom ADD COLUMN allowpm INT NULL;
UPDATE ofVersion SET version = 23 WHERE name = 'openfire';
