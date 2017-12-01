
-- jiveGroup: Recreate table from scratch
DROP TABLE jiveGroup;
CREATE TABLE jiveGroup (
  groupName             VARCHAR2(50)    NOT NULL,
  description           VARCHAR2(255),
  CONSTRAINT jiveGroup_pk PRIMARY KEY (groupName)
);


-- jiveGroupProp: Recreate table from scratch
DROP TABLE jiveGroupProp;
CREATE TABLE jiveGroupProp (
  groupName             VARCHAR(50)     NOT NULL,
  name                  VARCHAR2(100)   NOT NULL,
  propValue             VARCHAR2(4000)  NOT NULL,
  CONSTRAINT jiveGroupProp_pk PRIMARY KEY (groupName, name)
);


-- jiveGroupUser: Recreate table from scratch
DROP TABLE jiveGroupUser;
CREATE TABLE jiveGroupUser (
  groupName             VARCHAR(50)     NOT NULL,
  username              VARCHAR2(32)    NOT NULL,
  administrator         INTEGER         NOT NULL,
  CONSTRAINT jiveGroupUser PRIMARY KEY (groupName, username, administrator)
);


-- mucRoom: Add new columns: "lockedDate" and "emptyDate". Rename column "invitationRequired" to "membersOnly". Delete columns: "lastActiveDate" and "inMemory".
ALTER TABLE mucRoom ADD lockedDate CHAR(15) NOT NULL;
ALTER TABLE mucRoom ADD emptyDate CHAR(15) NULL;
ALTER TABLE mucRoom RENAME COLUMN invitationRequired TO membersOnly;
ALTER TABLE mucRoom DROP COLUMN lastActiveDate;
ALTER TABLE mucRoom DROP COLUMN inMemory;

-- mucMember: Add new columns
ALTER TABLE mucMember ADD firstName VARCHAR2(100) NULL;
ALTER TABLE mucMember ADD lastName  VARCHAR2(100) NULL;
ALTER TABLE mucMember ADD url VARCHAR2(100) NULL;
ALTER TABLE mucMember ADD email VARCHAR2(100) NULL;
ALTER TABLE mucMember ADD faqentry VARCHAR2(100) NULL;

-- mucConversationLog: Add new index
CREATE INDEX mucLog_time_idx ON mucConversationLog (time);

-- Deletes no longer needed entries
DELETE FROM jiveID where idType = 3;
DELETE FROM jiveID where idType = 4;

-- Add jiveVersion table
CREATE TABLE jiveVersion (
  majorVersion  INTEGER  NOT NULL,
  minorVersion  INTEGER  NOT NULL
);
INSERT INTO jiveVersion (majorVersion, minorVersion) VALUES (2, 1);

commit;
