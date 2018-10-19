// add columns for SASL SCRAM-SHA-1
ALTER TABLE ofUser ADD storedKey VARCHAR2(32);
ALTER TABLE ofUser ADD serverKey VARCHAR2(32);
ALTER TABLE ofUser ADD salt VARCHAR2(32);
ALTER TABLE ofUser ADD iterations INTEGER;

UPDATE ofVersion SET version = 22 WHERE name = 'openfire';

COMMIT;
