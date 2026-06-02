
ALTER TABLE mucConversationLog CHANGE time logTime CHAR(15) NOT NULL;

UPDATE jiveVersion set version=12 where name = 'openfire';
