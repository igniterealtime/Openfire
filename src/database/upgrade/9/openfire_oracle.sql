
-- Increase size of username field
ALTER TABLE jiveUser MODIFY username VARCHAR2(64);
ALTER TABLE jiveUserProp MODIFY username VARCHAR2(64);
ALTER TABLE jivePrivate MODIFY username VARCHAR2(64);
ALTER TABLE jiveOffline MODIFY username VARCHAR2(64);
ALTER TABLE jiveRoster MODIFY username VARCHAR2(64);
ALTER TABLE jiveVCard MODIFY username VARCHAR2(64);
ALTER TABLE jivePrivacyList MODIFY username VARCHAR2(64);

UPDATE jiveVersion set version=9 where name = 'openfire';

commit;
