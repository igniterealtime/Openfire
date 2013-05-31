-- Create SessionEntry 
CREATE TABLE ofGojaraSessions (
  username		VARCHAR(255) NOT NULL,
  transport		VARCHAR(255) NOT NULL,
  lastActivity		BIGINT NOT NULL,
  PRIMARY KEY (username, transport)
);
CREATE INDEX ofGojara_lastActivity_idx  ON ofGojaraSessions(lastActivity);

-- Update database version
UPDATE ofVersion SET version=1 WHERE name = 'gojara';