REM // $RCSfile$
REM // $Revision$
REM // $Date$

REM // upgrades from Messenger 2.1.x to 2.2.0

REM // Update jiveVersion to JM 2.2
UPDATE jiveVersion SET majorVersion=2, minorVersion=2

REM // jiveExtComponentConf: Create new table
CREATE TABLE jiveExtComponentConf (
  subdomain             VARCHAR2(255)    NOT NULL,
  secret                VARCHAR2(255),
  permission            VARCHAR2(10)     NOT NULL,
  CONSTRAINT jiveExtComponentConf_pk PRIMARY KEY (subdomain)
);

REM // jiveRemoteServerConf: Create new table
CREATE TABLE jiveRemoteServerConf (
  domain                VARCHAR2(255)    NOT NULL,
  remotePort            INTEGER,
  permission            VARCHAR2(10)     NOT NULL,
  CONSTRAINT jiveRemoteServerConf_pk PRIMARY KEY (domain)
);
