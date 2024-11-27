ALTER TABLE ofMucRoom ADD preserveHistOnDel INTEGER DEFAULT 1 NOT NULL;

UPDATE ofVersion SET version = 35 WHERE name = 'openfire';
