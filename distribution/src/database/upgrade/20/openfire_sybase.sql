/* add wildcard column to ofExtComponentConf */
ALTER TABLE ofExtComponentConf ADD COLUMN wildcard INT DEFAULT 0 NOT NULL;

/* Update version */
UPDATE ofVersion SET version = 20 WHERE name = 'openfire';
