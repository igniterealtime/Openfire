ALTER TABLE ofMucRoom ADD COLUMN tombstone INTEGER DEFAULT 0 NOT NULL;

CREATE TABLE ofMucRoomTombstone (
  serviceID           BIGINT        NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  CONSTRAINT ofMucRoomTombstone_pk PRIMARY KEY (serviceID, name)
);

UPDATE ofVersion SET version = 36 WHERE name = 'openfire';
