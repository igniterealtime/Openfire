
/* Increase size of username field */
ALTER TABLE jiveUser DROP CONSTRAINT jiveUser_pk;
ALTER TABLE jiveUser ALTER COLUMN username NVARCHAR(64) NOT NULL;
ALTER TABLE jiveUser ADD CONSTRAINT jiveUser_pk PRIMARY KEY (username);

ALTER TABLE jiveUserProp DROP CONSTRAINT jiveUserProp_pk;
ALTER TABLE jiveUserProp ALTER COLUMN username NVARCHAR(64) NOT NULL;
ALTER TABLE jiveUserProp ADD CONSTRAINT jiveUserProp_pk PRIMARY KEY (username, name);

ALTER TABLE jivePrivate DROP CONSTRAINT JivePrivate_pk;
ALTER TABLE jivePrivate ALTER COLUMN username NVARCHAR(64) NOT NULL;
ALTER TABLE jivePrivate ADD CONSTRAINT JivePrivate_pk PRIMARY KEY (username, name, namespace);

ALTER TABLE jiveOffline DROP CONSTRAINT jiveOffline_pk;
ALTER TABLE jiveOffline ALTER COLUMN username NVARCHAR(64) NOT NULL;
ALTER TABLE jiveOffline ADD CONSTRAINT jiveOffline_pk PRIMARY KEY (username, messageID);

DROP INDEX jiveRoster.jiveRoster_username_idx;
ALTER TABLE jiveRoster ALTER COLUMN username NVARCHAR(64) NOT NULL;
CREATE INDEX jiveRoster_username_idx ON jiveRoster (username ASC);

ALTER TABLE jiveVCard DROP CONSTRAINT JiveVCard_pk;
ALTER TABLE jiveVCard ALTER COLUMN username NVARCHAR(64) NOT NULL;
ALTER TABLE jiveVCard ADD CONSTRAINT JiveVCard_pk PRIMARY KEY (username);

DROP INDEX jivePrivacyList.jivePList_default_idx;
ALTER TABLE jivePrivacyList DROP CONSTRAINT jivePrivacyList_pk;
ALTER TABLE jivePrivacyList ALTER COLUMN username NVARCHAR(64) NOT NULL;
ALTER TABLE jivePrivacyList ADD CONSTRAINT jivePrivacyList_pk PRIMARY KEY (username, name);
CREATE INDEX jivePList_default_idx ON jivePrivacyList (username, isDefault);

UPDATE jiveVersion set version=9 where name = 'openfire';
