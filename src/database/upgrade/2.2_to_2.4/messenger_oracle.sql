REM // $RCSfile$
REM // $Revision: 795 $
REM // $Date: 2005-01-06 07:44:42 -0300 (Thu, 06 Jan 2005) $

REM // upgrades from Messenger 2.2.x to 2.4.0

REM // Update jiveVersion to JM 2.4
UPDATE jiveVersion SET majorVersion=2, minorVersion=4;

REM // jiveGroupUser: Alter length of username column
ALTER TABLE jiveGroupUser MODIFY username VARCHAR2(100) NOT NULL;
