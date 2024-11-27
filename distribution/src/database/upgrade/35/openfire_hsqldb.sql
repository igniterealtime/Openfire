ALTER TABLE ofMucRoom ADD COLUMN preserveHistOnDel INTEGER DEFAULT 1 NOT NULL;

UPDATE ofVersion SET version = 35 WHERE name = 'openfire';
