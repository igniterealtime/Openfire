-- Create SessionEntry
CREATE TABLE ofGojaraSessions(
  username			VARCHAR2(255) NOT NULL,
  transport			VARCHAR2(255) NOT NULL,
  lastActivity		NUMBER(10) NOT NULL,
  PRIMARY KEY (username, transport)
);

CREATE INDEX ofGojara_lastActivity_idx  ON ofGojaraSessions(lastActivity);

CREATE TABLE ofGojaraStatistics (
  logID 			NUMBER(10) NOT NULL AUTO_INCREMENT,
  messageDate		NUMBER(10) NOT NULL,
  messageType 		VARCHAR2(255) NOT NULL,
  fromJID 			VARCHAR2(255) NOT NULL,
  toJID 			VARCHAR2(255) NOT NULL,
  component			VARCHAR2(255) NOT NULL,
  PRIMARY KEY (logID)
);

INSERT INTO ofVersion (name, version) VALUES ('gojara', 1);
