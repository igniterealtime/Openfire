-- add isHidden column to mucService, with isHidden set to false by default
ALTER TABLE mucService ADD isHidden INTEGER DEFAULT 0 NOT NULL;


UPDATE jiveVersion set version=18 where name = 'openfire';

commit;
