
# Update jiveVersion to JM 2.4
UPDATE jiveVersion SET majorVersion=2, minorVersion=4;

# jiveGroupUser: Alter length of username column
ALTER TABLE jiveGroupUser CHANGE username username varchar(100) NOT NULL;
