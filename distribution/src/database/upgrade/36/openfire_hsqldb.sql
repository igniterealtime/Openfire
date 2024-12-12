ALTER TABLE ofMucRoom ADD COLUMN retireOnDeletion INTEGER DEFAULT 0 NOT NULL;

CREATE TABLE ofMucRoomRetiree (
  serviceID           BIGINT        NOT NULL,
  name                VARCHAR(50)   NOT NULL,
  retiredAt           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ofMucRoomRetiree_pk PRIMARY KEY (serviceID, name)
);

UPDATE ofVersion SET version = 36 WHERE name = 'openfire';
