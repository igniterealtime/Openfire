-- Create SessionEntry
CREATE TABLE ofGojaraSessions(
  username			varchar(max) NOT NULL,
  transport			varchar(max) NOT NULL,
  lastActivity		bigint(20) NOT NULL,
  PRIMARY KEY (username, transport)
);
CREATE INDEX ofGojara_lastActivity_idx  ON ofGojaraSessions(lastActivity);

CREATE TABLE ofGojaraStatistics (
  logID 			bigint(20) NOT NULL AUTO_INCREMENT,
  messageDate		bigint(20) NOT NULL,
  messageType 		tinytext NOT NULL,
  fromJID 			varchar(max) NOT NULL,
  toJID 			varchar(max) NOT NULL,
  component			varchar(max) NOT NULL,
  PRIMARY KEY (logID)
);

INSERT INTO ofVersion (name, version) VALUES ('gojara', 1);
