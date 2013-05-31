-- Create SessionEntry 
CREATE TABLE ofGojaraSessions(
  username			text NOT NULL,
  transport			text NOT NULL,
  lastActivity		bigint(20) NOT NULL,
  PRIMARY KEY (username, transport)
);
CREATE INDEX ofGojara_lastActivity_idx  ON ofGojaraSessions(lastActivity);

-- Update database version
UPDATE ofVersion SET version=1 WHERE name = 'gojara';
