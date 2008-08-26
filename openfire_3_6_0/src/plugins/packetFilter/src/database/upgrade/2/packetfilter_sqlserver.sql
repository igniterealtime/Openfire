/* Rename pfRules to ofPfRules */
sp_rename 'pfRules', 'ofPfRules';

UPDATE ofVersion set version=2 where name='packetfilter';