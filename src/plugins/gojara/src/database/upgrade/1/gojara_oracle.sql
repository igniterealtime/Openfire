-- Create SessionEntry 
CREATE TABLE ofGojaraSessions(
  username			VARCHAR2(255) NOT NULL,
  transport			VARCHAR2(255) NOT NULL,
  lastActivity		NUMBER(10) NOT NULL,
  PRIMARY KEY (username, transport)
);

CREATE INDEX ofGojara_lastActivity_idx  ON ofGojaraSessions(lastActivity);

-- Update database version
UPDATE ofVersion SET version=1 WHERE name = 'gojara';
