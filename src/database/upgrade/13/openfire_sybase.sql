/* $Revision:  $ */
/* $Date:  $ */

sp_rename 'jiveRemoteServerConf.domain', 'xmppDomain';

sp_rename 'jiveOffline.message', 'stanza';

sp_rename 'jiveVCard.value', 'vcard';

sp_rename 'jivePrivate.value', 'privateData';

sp_rename 'jiveUser.password', 'plainPassword';

sp_rename 'mucRoom.password', 'roomPassword';

UPDATE jiveVersion set version=13 where name = 'openfire';
