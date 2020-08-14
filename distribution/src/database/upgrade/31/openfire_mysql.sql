ALTER TABLE ofPubsubItem MODIFY payload LONGTEXT NULL;

UPDATE ofVersion SET version = 31 WHERE name = 'openfire';
