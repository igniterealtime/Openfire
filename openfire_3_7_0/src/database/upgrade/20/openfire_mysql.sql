# add wildcard column to ofExtComponentConf
ALTER TABLE ofExtComponentConf ADD COLUMN wildcard TINYINT NOT NULL DEFAULT 0;

# Update version
UPDATE ofVersion SET version = 20 WHERE name = 'openfire';
