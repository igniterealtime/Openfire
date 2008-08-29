# $Revision: 1650 $
# $Date: 2005-07-20 00:18:17 -0300 (Wed, 20 Jul 2005) $

# Update jiveVersion to JM 2.4
UPDATE jiveVersion SET majorVersion=2, minorVersion=4;

# jiveGroupUser: Alter length of username column
ALTER TABLE jiveGroupUser CHANGE username username varchar(100) NOT NULL;
