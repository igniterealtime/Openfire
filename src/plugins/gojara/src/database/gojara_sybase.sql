-- Create SessionEntry
CREATE TABLE ofGojaraSessions(
  username			text NOT NULL,
  transport			text NOT NULL,
  lastActivity		bigint(20) NOT NULL,
  PRIMARY KEY (username, transport)
);
CREATE INDEX ofGojara_lastActivity_idx  ON ofGojaraSessions(lastActivity);

CREATE TABLE ofGojaraStatistics (
  logID 			bigint(20) NOT NULL AUTO_INCREMENT,
  messageDate		bigint(20) NOT NULL,
  messageType 		tinytext NOT NULL,
  fromJID 			text NOT NULL,
  toJID 			text NOT NULL,
  component			text NOT NULL,
  PRIMARY KEY (logID)
);

INSERT INTO ofVersion (name, version) VALUES ('gojara', 1);
