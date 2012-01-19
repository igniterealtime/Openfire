

CREATE TABLE ofGojaraStatistics (
  logID 			bigserial NOT NULL,
  messageDate		bigint NOT NULL,
  messageType 		tinytext NOT NULL,
  fromJID 			varchar(255)  NOT NULL,
  toJID 			varchar(255 NOT NULL,
  component			varchar(255 NOT NULL,
  PRIMARY KEY (logID)
);

INSERT INTO ofVersion (name, version) VALUES ('gojara', 0);
