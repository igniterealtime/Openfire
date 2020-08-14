-- HyperSQL allows changing the type if all the existing values can be cast into the new type without string truncation or loss of significant digits.
ALTER TABLE ofPubsubItem ALTER COLUMN payload SET DATA TYPE CLOB;

UPDATE ofVersion SET version = 31 WHERE name = 'openfire';

