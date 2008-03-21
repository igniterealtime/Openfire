-- add autoLoad column to mucService
ALTER TABLE mucService ADD COLUMN autoLoad INTEGER NOT NULL;

-- set all current services to autoLoad = true
UPDATE mucService set autoLoad = 1;


UPDATE jiveVersion set version=18 where name = 'openfire';