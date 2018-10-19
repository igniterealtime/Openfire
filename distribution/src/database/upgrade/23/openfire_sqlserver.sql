ALTER TABLE ofMucRoom ADD allowpm INT NULL;
UPDATE ofVersion SET version = 23 WHERE name = 'openfire';
