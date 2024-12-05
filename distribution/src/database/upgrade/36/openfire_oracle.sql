ALTER TABLE ofMucRoom ADD tombstone INTEGER DEFAULT 0 NOT NULL;

CREATE TABLE ofMucRoomTombstone(
  serviceID           INT           NOT NULL,
  name                VARCHAR2(50)  NOT NULL,
  CONSTRAINT ofMucRoomTombstone_pk PRIMARY KEY (serviceID, name)
);

UPDATE ofVersion SET version = 36 WHERE name = 'openfire';
