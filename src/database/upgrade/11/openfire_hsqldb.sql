CREATE TABLE jivePresence (
  username              VARCHAR(64)     NOT NULL,
  offlinePresence       LONGVARCHAR,
  offlineDate           VARCHAR(15)     NOT NULL,
  CONSTRAINT jivePresence_pk PRIMARY KEY (username)
);

UPDATE jiveVersion set version=11 where name = 'openfire';
