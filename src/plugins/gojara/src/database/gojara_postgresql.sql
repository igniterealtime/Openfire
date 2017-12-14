-- Create SessionEntry
CREATE TABLE ofGojaraSessions(
  username 			varchar(255) NOT NULL,
  transport 		varchar(255) NOT NULL,
  lastActivity		BIGINT		 NOT NULL,
  PRIMARY KEY(username, transport)
);
CREATE INDEX ofGojara_lastActivity_idx  ON ofGojaraSessions(lastActivity);

CREATE TABLE ofGojaraStatistics (
  logID 			bigserial NOT NULL,
  messageDate		bigint NOT NULL,
  messageType 		varchar(255) NOT NULL,
  fromJID 			varchar(255)  NOT NULL,
  toJID 			varchar(255) NOT NULL,
  component			varchar(255) NOT NULL,
  PRIMARY KEY (logID)
);

INSERT INTO ofVersion (name, version) VALUES ('gojara', 1);
