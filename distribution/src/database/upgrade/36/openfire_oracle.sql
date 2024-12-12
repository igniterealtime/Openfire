ALTER TABLE ofMucRoom ADD retireOnDeletion INTEGER DEFAULT 0 NOT NULL;

CREATE TABLE ofMucRoomRetiree(
  serviceID           INT           NOT NULL,
  name                VARCHAR2(50)  NOT NULL,
  retiredAt           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ofMucRoomRetiree_pk PRIMARY KEY (serviceID, name)
);

UPDATE ofVersion SET version = 36 WHERE name = 'openfire';
