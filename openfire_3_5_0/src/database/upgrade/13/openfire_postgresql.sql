-- $Revision:  $
-- $Date:  $

ALTER TABLE jiveRemoteServerConf RENAME COLUMN domain TO xmppDomain;

ALTER TABLE jiveOffline RENAME COLUMN message TO stanza;

ALTER TABLE jiveVCard RENAME COLUMN value TO vcard;

ALTER TABLE jivePrivate RENAME COLUMN value TO privateData;

ALTER TABLE jiveUser RENAME COLUMN password TO plainPassword;

ALTER TABLE mucRoom RENAME COLUMN password TO roomPassword;

UPDATE jiveVersion set version=13 where name = 'openfire';
