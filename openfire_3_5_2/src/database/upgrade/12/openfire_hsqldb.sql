ALTER TABLE mucConversationLog ALTER COLUMN time RENAME TO logTime;

UPDATE jiveVersion set version=12 where name = 'openfire';