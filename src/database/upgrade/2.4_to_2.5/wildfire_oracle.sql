REM // $RCSfile$
REM // $Revision:  $
REM // $Date:  $

REM // upgrades from Wildfire 2.4.x to 2.5.0

REM // Update jiveVersion to Wildfire 2.5
UPDATE jiveVersion SET majorVersion=2, minorVersion=5;

REM // jivePrivacyList: Create new table
CREATE TABLE jivePrivacyList (
  username              VARCHAR2(32)    NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  isDefault             INTEGER         NOT NULL,
  list                  LONG            NOT NULL,
  CONSTRAINT jivePrivacyList_pk PRIMARY KEY (username, name)
);
CREATE INDEX jivePList_default_idx ON jivePrivacyList (username, isDefault);
