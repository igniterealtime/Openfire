ALTER TABLE ofPubsubItem ADD COLUMN label TEXT;
UPDATE ofVersion SET version = 26 WHERE name = 'openfire';
