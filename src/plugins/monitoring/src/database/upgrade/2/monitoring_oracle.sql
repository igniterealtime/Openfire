
-- Update database version
UPDATE ofVersion SET version = 2 WHERE name = 'monitoring';

commit;
