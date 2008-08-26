-- $Revision:  $
-- $Date:  $

-- Update jiveVersion to Openfire 2.5
UPDATE jiveVersion SET majorVersion=2, minorVersion=5;

-- jivePrivacyList: Create new table
CREATE TABLE jivePrivacyList (
  username              VARCHAR2(32)    NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  isDefault             INTEGER         NOT NULL,
  list                  LONG            NOT NULL,
  CONSTRAINT jivePrivacyList_pk PRIMARY KEY (username, name)
);
CREATE INDEX jivePList_default_idx ON jivePrivacyList (username, isDefault);

commit;