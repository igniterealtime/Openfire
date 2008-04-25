/* $Revision:  $ */
/* $Date:  $ */

sp_rename 'mucConversationLog.time', 'logTime';

UPDATE jiveVersion set version=12 where name = 'openfire';
