-- $Revision: 795 $
-- $Date: 2005-01-06 07:44:42 -0300 (Thu, 06 Jan 2005) $

-- Update the jiveVersion table to new definition.
DROP TABLE jiveVersion;
CREATE TABLE jiveVersion (
  name     VARCHAR2(50)  NOT NULL,
  version  INTEGER  NOT NULL,
  CONSTRAINT jiveVersion_pk PRIMARY KEY (name)
);
INSERT INTO jiveVersion (name, version) VALUES ('openfire', 6);

-- Make password column accept null, add encrypted password column.
ALTER TABLE jiveUser MODIFY password VARCHAR2(32) NULL;
ALTER TABLE jiveUser ADD encryptedPassword VARCHAR(255);

commit;