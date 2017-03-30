ALTER TABLE ofPubsubItem ADD COLUMN label MEDIUMTEXT NULL;
UPDATE ofVersion SET version = 26 WHERE name = 'openfire';
