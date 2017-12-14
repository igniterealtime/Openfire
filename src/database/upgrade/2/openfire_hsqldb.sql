
// Update jiveVersion to JM 2.2
UPDATE jiveVersion SET majorVersion=2, minorVersion=2;

// jiveExtComponentConf: Create new table
CREATE TABLE jiveExtComponentConf (
  subdomain             VARCHAR(255)    NOT NULL,
  secret                VARCHAR(255),
  permission            VARCHAR(10)     NOT NULL,
  CONSTRAINT jiveExtComponentConf_pk PRIMARY KEY (subdomain)
);

// jiveRemoteServerConf: Create new table
CREATE TABLE jiveRemoteServerConf (
  domain                VARCHAR(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR(10)     NOT NULL,
  CONSTRAINT jiveRemoteServerConf_pk PRIMARY KEY (domain)
);

// mucRoomProp: Create new table
CREATE TABLE mucRoomProp (
  roomID                BIGINT          NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(4000)   NOT NULL,
  CONSTRAINT mucRoomProp_pk PRIMARY KEY (roomID, name)
);

// mucRoom: Add new columns: "useReservedNick", "canChangeNick" and "canRegister".
ALTER TABLE mucRoom ADD COLUMN useReservedNick     INTEGER       DEFAULT 0 NOT NULL;
ALTER TABLE mucRoom ADD COLUMN canChangeNick       INTEGER       DEFAULT 1 NOT NULL;
ALTER TABLE mucRoom ADD COLUMN canRegister         INTEGER       DEFAULT 1 NOT NULL;

// jiveVCard: Recreate table from scratch
DROP TABLE jiveVCard;
CREATE TABLE jiveVCard (
  username              VARCHAR(32)     NOT NULL,
  value                 LONGVARCHAR     NOT NULL,
  CONSTRAINT jiveVCard_pk PRIMARY KEY (username)
);
