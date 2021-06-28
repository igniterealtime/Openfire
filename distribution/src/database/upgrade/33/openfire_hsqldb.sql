ALTER TABLE ofRoster ADD COLUMN stanza LONGVARCHAR NULL;

UPDATE ofVersion SET version = 33 WHERE name = 'openfire';
