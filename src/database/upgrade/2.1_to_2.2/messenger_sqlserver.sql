/* $RCSfile$ */
/* $Revision$                           */
/* $Date$               */

/* upgrades from Messenger 2.1.x to 2.2.0 */

/* Update jiveVersion to JM 2.2 */
UPDATE jiveVersion SET majorVersion=2, minorVersion=2

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
