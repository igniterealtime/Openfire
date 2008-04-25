-- $Revision:  $
-- $Date:  $

-- Increase size of username field

ALTER TABLE jiveUser ADD COLUMN temp varchar(64);
UPDATE jiveUser SET temp = username;
ALTER TABLE jiveUser DROP CONSTRAINT jiveUser_pk;
ALTER TABLE jiveUser DROP COLUMN username;
ALTER TABLE jiveUser RENAME COLUMN temp to username;
ALTER TABLE jiveUser ALTER COLUMN "username" SET NOT NULL;
ALTER TABLE jiveUser ADD CONSTRAINT jiveUser_pk PRIMARY KEY (username);

ALTER TABLE jiveUserProp ADD COLUMN temp varchar(64);
UPDATE jiveUserProp SET temp = username;
ALTER TABLE jiveUserProp DROP CONSTRAINT jiveUserProp_pk;
ALTER TABLE jiveUserProp DROP COLUMN username;
ALTER TABLE jiveUserProp RENAME COLUMN temp to username;
ALTER TABLE jiveUserProp ALTER COLUMN "username" SET NOT NULL;
ALTER TABLE jiveUserProp ADD CONSTRAINT jiveUserProp_pk PRIMARY KEY (username, name);

ALTER TABLE jivePrivate ADD COLUMN temp varchar(64);
UPDATE jivePrivate SET temp = username;
ALTER TABLE jivePrivate DROP CONSTRAINT jivePrivate_pk;
ALTER TABLE jivePrivate DROP COLUMN username;
ALTER TABLE jivePrivate RENAME COLUMN temp to username;
ALTER TABLE jivePrivate ALTER COLUMN "username" SET NOT NULL;
ALTER TABLE jivePrivate ADD CONSTRAINT jivePrivate_pk PRIMARY KEY (username, name, namespace);


ALTER TABLE jiveOffline ADD COLUMN temp varchar(64);
UPDATE jiveOffline SET temp = username;
ALTER TABLE jiveOffline DROP CONSTRAINT jiveOffline_pk;
ALTER TABLE jiveOffline DROP COLUMN username;
ALTER TABLE jiveOffline RENAME COLUMN temp to username;
ALTER TABLE jiveOffline ALTER COLUMN "username" SET NOT NULL;
ALTER TABLE jiveOffline ADD CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID);

ALTER TABLE jiveRoster ADD COLUMN temp varchar(64);
UPDATE jiveRoster SET temp = username;
DROP INDEX jiveRoster_username_idx;
ALTER TABLE jiveRoster DROP COLUMN username;
ALTER TABLE jiveRoster RENAME COLUMN temp to username;
ALTER TABLE jiveRoster ALTER COLUMN "username" SET NOT NULL;
CREATE INDEX jiveRoster_username_idx ON jiveRoster (username);

ALTER TABLE jiveVCard ADD COLUMN temp varchar(64);
UPDATE jiveVCard SET temp = username;
ALTER TABLE jiveVCard DROP CONSTRAINT jiveVCard_pk;
ALTER TABLE jiveVCard DROP COLUMN username;
ALTER TABLE jiveVCard RENAME COLUMN temp to username;
ALTER TABLE jiveVCard ALTER COLUMN "username" SET NOT NULL;
ALTER TABLE jiveVCard ADD CONSTRAINT jiveVCard_pk PRIMARY KEY (username);

ALTER TABLE jivePrivacyList ADD COLUMN temp varchar(64);
UPDATE jivePrivacyList SET temp = username;
ALTER TABLE jivePrivacyList DROP CONSTRAINT jivePrivacyList_pk;
DROP INDEX jivePList_default_idx;
ALTER TABLE jivePrivacyList DROP COLUMN username;
ALTER TABLE jivePrivacyList RENAME COLUMN temp to username;
ALTER TABLE jivePrivacyList ALTER COLUMN "username" SET NOT NULL;
ALTER TABLE jivePrivacyList ADD CONSTRAINT jivePrivacyList_pk PRIMARY KEY (username, name);
CREATE INDEX jivePList_default_idx ON jivePrivacyList (username, isDefault);

UPDATE jiveVersion set version=9 where name = 'openfire';
