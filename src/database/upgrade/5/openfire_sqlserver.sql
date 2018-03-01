
/* Update jiveVersion to Openfire 2.5 */
UPDATE jiveVersion SET majorVersion=2, minorVersion=5;


/* jivePrivacyList: Create new table */
CREATE TABLE jivePrivacyList (
  username              NVARCHAR(32)    NOT NULL,
  name                  NVARCHAR(100)   NOT NULL,
  isDefault             INT             NOT NULL,
  list                  NTEXT           NOT NULL,
  CONSTRAINT jivePrivacyList_pk PRIMARY KEY (username, name)
);
CREATE INDEX jivePList_default_idx ON jivePrivacyList (username, isDefault);
