
# Increase size of username field
ALTER TABLE jiveUser MODIFY username VARCHAR(64);
ALTER TABLE jiveUserProp MODIFY username VARCHAR(64);

ALTER TABLE jivePrivate DROP PRIMARY KEY;
ALTER TABLE jivePrivate MODIFY username VARCHAR(64);
ALTER TABLE jivePrivate ADD PRIMARY KEY (username, name, namespace(100));

ALTER TABLE jiveOffline MODIFY username VARCHAR(64);
ALTER TABLE jiveRoster MODIFY username VARCHAR(64);
ALTER TABLE jiveVCard MODIFY username VARCHAR(64);
ALTER TABLE jivePrivacyList MODIFY username VARCHAR(64);

# Increase size of column digest_frequency in pubsubSubscription
ALTER TABLE pubsubSubscription MODIFY digest_frequency INT NOT NULL;

UPDATE jiveVersion set version=9 where name = 'openfire';
