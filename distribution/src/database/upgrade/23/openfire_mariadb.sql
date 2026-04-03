ALTER TABLE ofMucRoom ADD COLUMN allowpm TINYINT NULL;
UPDATE ofVersion SET version = 23 WHERE name = 'openfire';
