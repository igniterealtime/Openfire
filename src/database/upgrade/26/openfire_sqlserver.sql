ALTER TABLE ofPubsubItem ADD COLUMN label NTEXT;
UPDATE ofVersion SET version = 26 WHERE name = 'openfire';
