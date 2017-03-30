ALTER TABLE ofPubsubItem ADD COLUMN label clob;
UPDATE ofVersion SET version = 26 WHERE name = 'openfire';
