

CREATE TABLE ofGojaraStatistics (
  logID 			Integer Identity NOT NULL,
  messageDate		bigint(20) NOT NULL,
  messageType 		VARCHAR(255) NOT NULL,
  fromJID 			VARCHAR(255) NOT NULL,
  toJID 			VARCHAR(255) NOT NULL,
  component			VARCHAR(255) NOT NULL,
  PRIMARY KEY (logID)
);

INSERT INTO ofVersion (name, version) VALUES ('gojara', 0);
