

CREATE TABLE ofGojaraStatistics (
  logID 			NUMBER(10) NOT NULL AUTO_INCREMENT,
  messageDate		NUMBER(10) NOT NULL,
  messageType 		VARCHAR2(255) NOT NULL,
  fromJID 			VARCHAR2(255) NOT NULL,
  toJID 			VARCHAR2(255) NOT NULL,
  component			VARCHAR2(255) NOT NULL,
  PRIMARY KEY (logID)
);

INSERT INTO ofVersion (name, version) VALUES ('gojara', 0);
