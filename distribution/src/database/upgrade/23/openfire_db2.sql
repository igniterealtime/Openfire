ALTER TABLE ofMucRoom ADD COLUMN allowpm INTEGER NULL;
UPDATE ofVersion SET version = 23 WHERE name = 'openfire';
