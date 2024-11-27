ALTER TABLE ofMucRoom ADD COLUMN preserveHistOnDel TINYINT NOT NULL DEFAULT 1;

UPDATE ofVersion SET version = 35 WHERE name = 'openfire';
