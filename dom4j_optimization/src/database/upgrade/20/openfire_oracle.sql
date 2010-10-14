-- add wildcard column to ofExtComponentConf
ALTER TABLE ofExtComponentConf ADD wildcard INTEGER DEFAULT 0 NOT NULL;

-- Update version
UPDATE ofVersion SET version = 20 WHERE name = 'openfire';

COMMIT;