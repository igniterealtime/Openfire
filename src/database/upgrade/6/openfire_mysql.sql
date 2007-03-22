# $Revision:  $
# $Date:  $

# Update the jiveVersion table to new definition.
DROP TABLE jiveVersion;
CREATE TABLE jiveVersion (
  name     VARCHAR(50)  NOT NULL,
  version  INTEGER  NOT NULL,
  PRIMARY KEY (name)
);
INSERT INTO jiveVersion (name, version) VALUES ('openfire', 6);

# Make password column accept null, add encrypted password column.
ALTER TABLE jiveUser MODIFY password VARCHAR(32) NULL;
ALTER TABLE jiveUser ADD COLUMN encryptedPassword VARCHAR(255) NULL AFTER password;