-- $Revision: 1650 $
-- $Date: 2005-07-20 00:18:17 -0300 (Wed, 20 Jul 2005) $

-- Update jiveVersion to JM 2.2
UPDATE jiveVersion SET majorVersion=2, minorVersion=2;

-- jiveExtComponentConf: Create new table
CREATE TABLE jiveExtComponentConf (
  subdomain             VARCHAR2(255)    NOT NULL,
  secret                VARCHAR2(255),
  permission            VARCHAR2(10)     NOT NULL,
  CONSTRAINT jiveExtComponentConf_pk PRIMARY KEY (subdomain)
);

-- jiveRemoteServerConf: Create new table
CREATE TABLE jiveRemoteServerConf (
  domain                VARCHAR2(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR2(10)     NOT NULL,
  CONSTRAINT jiveRemoteServerConf_pk PRIMARY KEY (domain)
);

-- mucRoomProp: Create new table
CREATE TABLE mucRoomProp (
  roomID                INT             NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(1024)  NOT NULL,
  CONSTRAINT mucRoomProp_pk PRIMARY KEY (roomID, name)
);

-- mucRoom: Add new columns: "useReservedNick", "canChangeNick" and "canRegister".
ALTER TABLE mucRoom ADD useReservedNick INTEGER NULL;
ALTER TABLE mucRoom ADD canChangeNick INTEGER NULL;
ALTER TABLE mucRoom ADD canRegister INTEGER NULL;
UPDATE mucRoom set useReservedNick=0, canChangeNick=1, canRegister=1;
ALTER TABLE mucRoom MODIFY useReservedNick INTEGER NOT NULL;
ALTER TABLE mucRoom MODIFY canChangeNick INTEGER NOT NULL;
ALTER TABLE mucRoom MODIFY canRegister INTEGER NOT NULL;

-- jiveVCard: Recreate table from scratch
DROP TABLE jiveVCard;
CREATE TABLE jiveVCard (
  username              VARCHAR2(32)    NOT NULL,
  value                 LONG            NOT NULL,
  CONSTRAINT JiveVCard_pk PRIMARY KEY (username)
);

commit;