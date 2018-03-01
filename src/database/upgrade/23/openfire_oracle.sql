ALTER TABLE ofMucRoom ADD allowpm INTEGER NULL;
UPDATE ofVersion SET version = 23 WHERE name = 'openfire';
COMMIT;
