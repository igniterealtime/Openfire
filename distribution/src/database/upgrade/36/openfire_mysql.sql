ALTER TABLE ofMucRoom ADD COLUMN retireOnDeletion TINYINT NOT NULL DEFAULT 0;

CREATE TABLE ofMucRoomRetiree (
  serviceID           BIGINT        NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  alternateJID        VARCHAR(2000),
  reason              VARCHAR(1024),
  retiredAt           CHAR(15)      NOT NULL,
  PRIMARY KEY (serviceID,name)
);

UPDATE ofVersion SET version = 36 WHERE name = 'openfire';
