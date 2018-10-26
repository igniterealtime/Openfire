-- Only when the update in 27 succeeded, drop the table that was used as its source.
DROP TABLE ofPrivate;

-- Update version
UPDATE ofVersion SET version = 28 WHERE name = 'openfire';
