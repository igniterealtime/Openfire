// Rename pfRules to ofPfRules
DROP INDEX pfRules_idx;
ALTER TABLE pfRules DROP CONSTRAINT pfRules_pk;
ALTER TABLE pfRules RENAME TO ofPfRules;
ALTER TABLE ofPfRules ADD CONSTRAINT ofPfRules_pk PRIMARY KEY (id);

UPDATE ofVersion set version=2 where name='packetfilter';