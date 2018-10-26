
-- Update jiveVersion to JM 2.2
UPDATE jiveVersion SET majorVersion=2, minorVersion=2;

-- jiveExtComponentConf: Create new table
CREATE TABLE jiveExtComponentConf (
  subdomain             VARCHAR(255)    NOT NULL,
  secret                VARCHAR(255),
  permission            VARCHAR(10)     NOT NULL,
  CONSTRAINT jiveExtComponentConf_pk PRIMARY KEY (subdomain)
);

-- jiveRemoteServerConf: Create new table
CREATE TABLE jiveRemoteServerConf (
  domain                VARCHAR(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR(10)     NOT NULL,
  CONSTRAINT jiveRemoteServerConf_pk PRIMARY KEY (domain)
);

-- mucRoomProp: Create new table
CREATE TABLE mucRoomProp (
  roomID                INTEGER         NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(2000)   NOT NULL,
  CONSTRAINT mucRoomProp_pk PRIMARY KEY (roomID, name)
);

-- mucRoom: Add new columns: "useReservedNick", "canChangeNick" and "canRegister".
ALTER TABLE mucRoom ADD COLUMN useReservedNick     INTEGER       NOT NULL;
ALTER TABLE mucRoom ADD COLUMN canChangeNick       INTEGER       NOT NULL;
ALTER TABLE mucRoom ADD COLUMN canRegister         INTEGER       NOT NULL;

UPDATE mucRoom set useReservedNick=0, canChangeNick=1, canRegister=1;

-- jiveVCard: Recreate table from scratch
DROP TABLE jiveVCard;
CREATE TABLE jiveVCard (
  username              VARCHAR(32)     NOT NULL,
  value                 LONG VARCHAR    NOT NULL,
  CONSTRAINT jiveVCard_pk PRIMARY KEY (username)
);
