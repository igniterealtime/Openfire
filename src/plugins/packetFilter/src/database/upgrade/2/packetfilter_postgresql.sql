-- Rename pfRules to ofPfRules
ALTER TABLE pfRules DROP CONSTRAINT pfRules_id;
ALTER TABLE pfRules RENAME TO ofPfRules;
ALTER TABLE ofPfRules ADD CONSTRAINT ofPfRules_id PRIMARY KEY(id);

UPDATE ofVersion set version=2 where name='packetfilter';
