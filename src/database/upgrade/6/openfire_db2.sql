
-- Update the jiveVersion table to new definition.
DROP TABLE jiveVersion;
CREATE TABLE jiveVersion (
  name     varchar(50)  NOT NULL,
  version  INTEGER  NOT NULL,
  CONSTRAINT jiveVersion_pk PRIMARY KEY (name)
);
INSERT INTO jiveVersion (name, version) VALUES ('openfire', 6);

-- Make password column accept null, add encrypted password column.
ALTER TABLE jiveUser ALTER COLUMN password SET DATA TYPE VARCHAR(32);
ALTER TABLE jiveUser ADD COLUMN encryptedPassword VARCHAR(255);
