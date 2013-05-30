-- Create SessionEntry 
CREATE TABLE ofGojaraSessions(
  username 			varchar(255) NOT NULL,
  transport 		varchar(255) NOT NULL,
  lastActivity		BIGINT		 NOT NULL,
  PRIMARY KEY(username, transport)
);
CREATE INDEX ofGojara_lastActivity_idx  ON ofGojaraSessions(lastActivity);

-- Update database version
UPDATE ofVersion SET version=1 WHERE name = 'gojara';