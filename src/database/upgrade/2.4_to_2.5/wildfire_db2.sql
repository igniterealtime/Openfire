-- $RCSfile$
-- $Revision:  $
-- $Date:  $

-- upgrades from Wildfire 2.4.x to 2.5.0

-- Update jiveVersion to Wildfire 2.5
UPDATE jiveVersion SET majorVersion=2, minorVersion=5;

-- jivePrivacyList: Create new table
CREATE TABLE jivePrivacyList (
  username              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  isDefault             INTEGER         NOT NULL,
  list                  VARCHAR(2000)   NOT NULL,
  CONSTRAINT jivePrivacyList_pk PRIMARY KEY (username, name)
);
CREATE INDEX jivePList_default_idx ON jivePrivacyList (username, isDefault);
