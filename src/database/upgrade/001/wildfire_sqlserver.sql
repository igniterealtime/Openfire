/* $Revision: 840 $                                         */
/* $Date: 2005-01-10 01:30:06 -0300 (Mon, 10 Jan 2005) $    */

/* jiveGroup: Recreate table from scratch */
DROP TABLE jiveGroup;
CREATE TABLE jiveGroup (
  groupName             NVARCHAR(50)   NOT NULL,
  description           NVARCHAR(255),
  CONSTRAINT group_pk PRIMARY KEY (groupName)
);

/* jiveGroupProp: Recreate table from scratch */
DROP TABLE jiveGroupProp;
CREATE TABLE jiveGroupProp (
   groupName            NVARCHAR(50)   NOT NULL,
   name                 NVARCHAR(100)   NOT NULL,
   propValue            NVARCHAR(2000)  NOT NULL,
   CONSTRAINT jiveGroupProp_pk PRIMARY KEY (groupName, name)
);


/* jiveGroupUser: Recreate table from scratch */
DROP TABLE jiveGroupUser;
CREATE TABLE jiveGroupUser (
  groupName             NVARCHAR(50)   NOT NULL,
  username              NVARCHAR(32)    NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser_pk PRIMARY KEY (groupName, username, administrator)
);

/* mucRoom: Add new columns: "lockedDate" and "emptyDate". Rename column "invitationRequired" to "membersOnly". Delete columns: "lastActiveDate" and "inMemory". */
ALTER TABLE mucRoom ADD lockedDate CHAR(15) NOT NULL;
ALTER TABLE mucRoom ADD emptyDate CHAR(15) NULL;
ALTER TABLE mucRoom ADD membersOnly INT NOT NULL;
UPDATE mucRoom SET membersOnly = invitationRequired;
ALTER TABLE mucRoom DROP COLUMN invitationRequired;
ALTER TABLE mucRoom DROP COLUMN lastActiveDate;
ALTER TABLE mucRoom DROP COLUMN inMemory;

/* mucMember: Add new columns */
ALTER TABLE mucMember ADD firstName NVARCHAR(100) NULL;
ALTER TABLE mucMember ADD lastName  NVARCHAR(100) NULL;
ALTER TABLE mucMember ADD url NVARCHAR(100) NULL;
ALTER TABLE mucMember ADD email NVARCHAR(100) NULL;
ALTER TABLE mucMember ADD faqentry NVARCHAR(100) NULL;

/* mucConversationLog: Add new index */
CREATE INDEX mucLog_time_idx ON mucConversationLog (time);

/* Deletes no longer needed entries */
DELETE FROM jiveID where idType = 3;
DELETE FROM jiveID where idType = 4;

/* Add jiveVersion table */
CREATE TABLE jiveVersion (
  majorVersion  INTEGER  NOT NULL,
  minorVersion  INTEGER  NOT NULL
);
INSERT INTO jiveVersion (majorVersion, minorVersion) VALUES (2, 1);