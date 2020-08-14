ALTER TABLE ofPubsubItem ADD ( temp clob NULL );
UPDATE ofPubsubItem SET temp=payload, payload=null;
ALTER TABLE ofPubsubItem DROP COLUMN y;
ALTER TABLE ofPubsubItem RENAME COLUMN temp TO payload;

UPDATE ofVersion SET version = 31 WHERE name = 'openfire';
