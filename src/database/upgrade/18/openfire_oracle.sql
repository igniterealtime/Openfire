-- add isHidden column to mucService
ALTER TABLE mucService ADD isHidden INTEGER;

-- set all current services to isHidden = false
UPDATE mucService set isHidden = 0;


UPDATE jiveVersion set version=18 where name = 'openfire';

commit;