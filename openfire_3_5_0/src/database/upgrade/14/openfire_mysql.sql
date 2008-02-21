# jiveRoster: Add new index
ALTER TABLE jiveRoster ADD INDEX jiveRoster_jid_idx (jid(1024));

UPDATE jiveVersion set version=14 where name = 'openfire';