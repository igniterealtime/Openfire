
ALTER TABLE mucConversationLog RENAME COLUMN time TO logTime;

UPDATE jiveVersion set version=12 where name = 'openfire';
