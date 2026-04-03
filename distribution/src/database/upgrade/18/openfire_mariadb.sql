# add isHidden column to mucService, with isHidden set to false by default
ALTER TABLE mucService ADD COLUMN isHidden TINYINT NOT NULL DEFAULT 0;


UPDATE jiveVersion set version=18 where name = 'openfire';
