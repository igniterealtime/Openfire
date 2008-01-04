ALTER TABLE jiveRemoteServerConf ALTER COLUMN domain RENAME TO xmppDomain;

ALTER TABLE jiveOffline ALTER COLUMN message RENAME TO stanza;

ALTER TABLE jiveVCard ALTER COLUMN value RENAME TO vcard;

ALTER TABLE jivePrivate ALTER COLUMN value RENAME TO privateData;

ALTER TABLE jiveUser ALTER COLUMN password RENAME TO plainPassword;

ALTER TABLE mucRoom ALTER COLUMN password RENAME TO roomPassword;

UPDATE jiveVersion set version=13 where name = 'openfire';