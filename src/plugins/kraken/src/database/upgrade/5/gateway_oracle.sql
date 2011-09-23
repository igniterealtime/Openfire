-- Update database version
UPDATE jiveVersion SET version = 5 WHERE name = 'gateway';

commit;
