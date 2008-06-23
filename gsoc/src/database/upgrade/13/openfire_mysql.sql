# $Revision:  $
# $Date:  $

ALTER TABLE pubsubItem CHANGE payload payload MEDIUMTEXT;

ALTER TABLE jiveRemoteServerConf CHANGE domain xmppDomain VARCHAR(255) NOT NULL;

ALTER TABLE jiveOffline CHANGE message stanza TEXT NOT NULL;

ALTER TABLE jiveVCard CHANGE value vcard MEDIUMTEXT NOT NULL;

ALTER TABLE jivePrivate CHANGE value privateData TEXT NOT NULL;

ALTER TABLE jiveUser CHANGE password plainPassword VARCHAR(32);

ALTER TABLE mucRoom CHANGE password roomPassword VARCHAR(50);

UPDATE jiveVersion set version=13 where name = 'openfire';