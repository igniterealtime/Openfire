ALTER TABLE ofMucRoom ADD retireOnDeletion INT DEFAULT 0 NOT NULL;

CREATE TABLE ofMucRoomRetiree (
  serviceID           INT           NOT NULL,
  name                NVARCHAR(50)  NOT NULL,
  alternateJID        NVARCHAR(2000),
  reason              NVARCHAR(1024),
  retiredAt           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ofMucRoomRetiree_pk PRIMARY KEY (serviceID, name)
)

UPDATE ofVersion SET version = 36 WHERE name = 'openfire';
