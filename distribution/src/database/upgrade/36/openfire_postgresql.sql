ALTER TABLE ofMucRoom ADD COLUMN retireOnDeletion INTEGER DEFAULT 0 NOT NULL;

CREATE TABLE ofMucRoomRetiree (
  serviceID           INTEGER       NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  CONSTRAINT ofMucRoomRetiree_pk PRIMARY KEY (serviceID, name)
);

UPDATE ofVersion SET version = 36 WHERE name = 'openfire';
