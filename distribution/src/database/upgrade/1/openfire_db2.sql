
-- jiveGroup: Recreate table from scratch
DROP TABLE jiveGroup;
CREATE TABLE jiveGroup (
  groupName             VARCHAR(50)     NOT NULL,
  description           VARCHAR(255),
  CONSTRAINT jiveGroup_pk PRIMARY KEY (groupName)
);

-- jiveGroupProp: Recreate table from scratch
DROP TABLE jiveGroupProp;
CREATE TABLE jiveGroupProp (
  groupName             VARCHAR(50)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  propValue             VARCHAR(2000)   NOT NULL,
  CONSTRAINT jiveGrpProp_pk PRIMARY KEY (groupName, name)
);

-- jiveGroupUser: Recreate table from scratch
DROP TABLE jiveGroupUser;
CREATE TABLE jiveGroupUser (
  groupName             VARCHAR(50)     NOT NULL,
  username              VARCHAR(32)     NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGrpUser PRIMARY KEY (groupName, username, administrator)
);

-- mucRoom: Add new columns: "lockedDate" and "emptyDate". Rename column "invitationRequired" to "membersOnly". Delete columns: "lastActiveDate" and "inMemory".
ALTER TABLE mucRoom ADD COLUMN lockedDate CHAR(15) NOT NULL;
ALTER TABLE mucRoom ADD COLUMN emptyDate CHAR(15) NULL;
ALTER TABLE mucRoom RENAME COLUMN invitationRequired TO membersOnly;
ALTER TABLE mucRoom DROP COLUMN lastActiveDate;
ALTER TABLE mucRoom DROP COLUMN inMemory;

-- mucMember: Add new columns
ALTER TABLE mucMember ADD COLUMN firstName VARCHAR(100) NULL;
ALTER TABLE mucMember ADD COLUMN lastName  VARCHAR(100) NULL;
ALTER TABLE mucMember ADD COLUMN url VARCHAR(100) NULL;
ALTER TABLE mucMember ADD COLUMN email VARCHAR(100) NULL;
ALTER TABLE mucMember ADD COLUMN faqentry VARCHAR(100) NULL;

-- mucConversationLog: Add new index
CREATE INDEX mucLog_time_idx ON mucConversationLog (time);

-- Deletes no longer needed entries
DELETE FROM jiveID where idType = 3;
DELETE FROM jiveID where idType = 4;

-- Add jiveVersion 
CREATE TABLE jiveVersion (
  majorVersion  INTEGER  NOT NULL,
  minorVersion  INTEGER  NOT NULL
);
INSERT INTO jiveVersion (majorVersion, minorVersion) VALUES (2, 1);
