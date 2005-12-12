/* $RCSfile$ */
/* $Revision: 840 $                           */
/* $Date: 2005-01-10 01:30:06 -0300 (Mon, 10 Jan 2005) $               */

/* upgrades from Messenger 2.2.x to 2.4.0 */

/* Update jiveVersion to JM 2.4 */
UPDATE jiveVersion SET majorVersion=2, minorVersion=4;


/* jiveGroupUser: Alter length of username column */
ALTER TABLE jiveGroupUser ALTER COLUMN username NVARCHAR(100) NOT NULL;
