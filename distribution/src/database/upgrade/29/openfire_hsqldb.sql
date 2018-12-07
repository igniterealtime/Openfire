// Only when the update in 28 succeeded, drop the table that was used as its source.
DROP TABLE ofPrivate;

// Update version
UPDATE ofVersion SET version = 29 WHERE name = 'openfire';
