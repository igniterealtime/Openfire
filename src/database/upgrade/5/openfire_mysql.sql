# $Revision:  $
# $Date:  $

# Update jiveVersion to Openfire 2.5
UPDATE jiveVersion SET majorVersion=2, minorVersion=5;

# jivePrivacyList: Create new table
CREATE TABLE jivePrivacyList (
  username              VARCHAR(32)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  isDefault             TINYINT         NOT NULL,
  list                  TEXT            NOT NULL,
  PRIMARY KEY (username, name),
  INDEX jivePList_default_idx (username, isDefault)
);