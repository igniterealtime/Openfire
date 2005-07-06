/* $RCSfile$ */
/* $Revision$                           */
/* $Date$               */

/* upgrades from Messenger 2.1.x to 2.2.0 */

/* Update jiveVersion to JM 2.2 */
UPDATE jiveVersion SET majorVersion=2, minorVersion=2;

/* jiveExtComponentConf: Create new table */
CREATE TABLE jiveExtComponentConf (
  subdomain             NVARCHAR(255)    NOT NULL,
  secret                NVARCHAR(255),
  permission            NVARCHAR(10)     NOT NULL,
  CONSTRAINT jiveExtComponentConf_pk PRIMARY KEY (subdomain)
);

/* jiveRemoteServerConf: Create new table */
CREATE TABLE jiveRemoteServerConf (
  domain                NVARCHAR(255)    NOT NULL,
  remotePort            INTEGER,
  permission            NVARCHAR(10)     NOT NULL,
  CONSTRAINT jiveRemoteServerConf_pk PRIMARY KEY (domain)
);

/* mucRoomProp: Create new table */
CREATE TABLE mucRoomProp (
  roomID                INT             NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  propValue             NVARCHAR(2000)  NOT NULL,
  CONSTRAINT mucRoomProp_pk PRIMARY KEY (roomID, name)
);

/* mucRoom: Add new columns: "useReservedNick" and "canChangeNick". */
ALTER TABLE mucRoom ADD useReservedNick     INT           NOT NULL;
ALTER TABLE mucRoom ADD canChangeNick       INT           NOT NULL;
