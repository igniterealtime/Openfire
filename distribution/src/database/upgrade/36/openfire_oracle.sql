ALTER TABLE ofMucRoom ADD retireOnDeletion INTEGER DEFAULT 0 NOT NULL;

CREATE TABLE ofMucRoomRetiree(
  serviceID           INT           NOT NULL,
  name                VARCHAR2(50)  NOT NULL,
  alternateJID        VARCHAR2(2000),
  reason              VARCHAR2(1024),
  retiredAt           CHAR(15)      NOT NULL,
  CONSTRAINT ofMucRoomRetiree_pk PRIMARY KEY (serviceID, name)
);

UPDATE ofVersion SET version = 36 WHERE name = 'openfire';
