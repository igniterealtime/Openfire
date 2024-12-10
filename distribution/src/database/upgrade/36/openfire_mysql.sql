ALTER TABLE ofMucRoom ADD COLUMN retireOnDeletion TINYINT NOT NULL DEFAULT 0;

CREATE TABLE ofMucRoomRetiree (
  serviceID           BIGINT        NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  PRIMARY KEY (serviceID,name)
);

UPDATE ofVersion SET version = 36 WHERE name = 'openfire';
