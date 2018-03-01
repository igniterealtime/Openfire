-- jiveRoster: Add new index
CREATE INDEX jiveR_jid_idx ON jiveRoster (jid ASC);

UPDATE jiveVersion set version=14 where name = 'openfire';
