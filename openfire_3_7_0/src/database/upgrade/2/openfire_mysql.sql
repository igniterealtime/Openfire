# $Revision: 1650 $
# $Date: 2005-07-20 00:18:17 -0300 (Wed, 20 Jul 2005) $

# Update jiveVersion to JM 2.2
UPDATE jiveVersion SET majorVersion=2, minorVersion=2;

# jiveExtComponentConf: Create new table
CREATE TABLE jiveExtComponentConf (
  subdomain             VARCHAR(255)    NOT NULL,
  secret                VARCHAR(255),
  permission            VARCHAR(10)     NOT NULL,
  PRIMARY KEY (subdomain)
);

# jiveRemoteServerConf: Create new table
CREATE TABLE jiveRemoteServerConf (
  domain                VARCHAR(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR(10)     NOT NULL,
  PRIMARY KEY (domain)
);

# mucRoomProp: Create new table
CREATE TABLE mucRoomProp (
  roomID                BIGINT          NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             TEXT            NOT NULL,
  PRIMARY KEY (roomID, name)
);

# mucRoom: Add new columns: "useReservedNick", "canChangeNick" and "canRegister".
ALTER TABLE mucRoom ADD COLUMN useReservedNick     TINYINT       NOT NULL;
ALTER TABLE mucRoom ADD COLUMN canChangeNick       TINYINT       NOT NULL;
ALTER TABLE mucRoom ADD COLUMN canRegister         TINYINT       NOT NULL;

UPDATE mucRoom set useReservedNick=0, canChangeNick=1, canRegister=1;

# jiveVCard: Recreate table from scratch
DROP TABLE jiveVCard;
CREATE TABLE jiveVCard (
  username              VARCHAR(32)     NOT NULL,
  value                 TEXT            NOT NULL,
  PRIMARY KEY (username)
);