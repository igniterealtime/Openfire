// add columns for SASL SCRAM-SHA-1
ALTER TABLE ofUser ADD COLUMN storedKey VARCHAR(32);
ALTER TABLE ofUser ADD COLUMN serverKey VARCHAR(32);
ALTER TABLE ofUser ADD COLUMN salt VARCHAR(32);
ALTER TABLE ofUser ADD COLUMN iterations INTEGER;

UPDATE ofVersion SET version = 22 WHERE name = 'openfire';
