# Rename pfRules to ofPfRules
RENAME TABLE pfRules TO ofPfRules;

UPDATE ofVersion set version=2 where name='packetfilter';