ALTER TABLE ofRoster ADD COLUMN stanza TEXT NULL;

UPDATE ofVersion SET version = 33 WHERE name = 'openfire';
