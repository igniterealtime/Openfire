// $Revision: 795 $
// $Date: 2005-01-06 07:44:42 -0300 (Thu, 06 Jan 2005) $

// Update jiveVersion to JM 2.4
UPDATE jiveVersion SET majorVersion=2, minorVersion=4;

// jiveGroupUser: Alter length of username column
ALTER TABLE jiveGroupUser ALTER COLUMN username VARCHAR(100) NOT NULL;
