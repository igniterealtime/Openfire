// add salt and salted password for SASL SCRAM-SHA-1
ALTER TABLE ofUser ADD COLUMN hi VARCHAR(64);
ALTER TABLE ofUser ADD COLUMN salt VARCHAR(32);

UPDATE ofVersion SET version = 22 WHERE name = 'openfire';

COMMIT;
