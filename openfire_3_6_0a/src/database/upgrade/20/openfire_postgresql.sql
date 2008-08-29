-- add wildcard column to ofExtComponentConf
ALTER TABLE ofExtComponentConf ADD COLUMN wildcard INTEGER;
UPDATE ofExtComponentConf SET wildcard = 0;

-- Update version
UPDATE ofVersion SET version = 20 WHERE name = 'openfire';
