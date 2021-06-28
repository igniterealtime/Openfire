ALTER TABLE ofRoster ADD COLUMN stanza CLOB;

UPDATE ofVersion SET version = 33 WHERE name = 'openfire';
