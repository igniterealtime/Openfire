# $RCSfile$
# $Revision$
# $Date$

# upgrades from Messenger 2.1.x to 2.2.0

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

# mucRoom: Add new columns: "useReservedNick" and "canChangeNick".
ALTER TABLE mucRoom ADD COLUMN useReservedNick     TINYINT       NOT NULL;
ALTER TABLE mucRoom ADD COLUMN canChangeNick       TINYINT       NOT NULL;

