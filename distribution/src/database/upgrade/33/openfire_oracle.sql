ALTER TABLE ofRoster ADD stanza VARCHAR2(4000) NULL;

UPDATE ofVersion SET version = 33 WHERE name = 'openfire';
