# jiveRoster: Change jid column to varchar
ALTER TABLE jiveRoster CHANGE COLUMN jid jid varchar(1024) not null;

# jiveRoster: Add new index
ALTER TABLE jiveRoster ADD INDEX jiveRoster_jid_idx (jid);

UPDATE jiveVersion set version=14 where name = 'openfire';
