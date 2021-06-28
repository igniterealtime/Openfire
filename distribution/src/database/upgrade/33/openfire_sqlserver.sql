ALTER TABLE ofRoster ADD stanza NTEXT NULL;

UPDATE ofVersion SET version = 33 WHERE name = 'openfire';
