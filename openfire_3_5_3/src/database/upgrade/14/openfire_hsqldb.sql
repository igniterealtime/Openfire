// jiveRoster: Add new index
CREATE INDEX jiveRoster_jid_idx ON jiveRoster (jid);

UPDATE jiveVersion set version=14 where name = 'openfire';