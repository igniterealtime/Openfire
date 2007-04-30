// $Revision:  $
// $Date:  $

// Increase size of username field
ALTER TABLE jiveUser ALTER COLUMN username VARCHAR(64);
ALTER TABLE jiveUserProp ALTER COLUMN username VARCHAR(64);
ALTER TABLE jivePrivate ALTER COLUMN username VARCHAR(64);
ALTER TABLE jiveOffline ALTER COLUMN username VARCHAR(64);

DROP INDEX jiveRoster_username_idx;
ALTER TABLE jiveRoster ALTER COLUMN username VARCHAR(64);
CREATE INDEX jiveRoster_username_idx ON jiveRoster (username);

ALTER TABLE jiveVCard ALTER COLUMN username VARCHAR(64);

DROP INDEX jivePList_default_idx;
ALTER TABLE jivePrivacyList ALTER COLUMN username VARCHAR(64);
CREATE INDEX jivePList_default_idx ON jivePrivacyList (username, isDefault);

UPDATE jiveVersion set version=9 where name = 'openfire';
