
CREATE TABLE jivePresence (
  username              NVARCHAR(64)    NOT NULL,
  offlinePresence       TEXT,
  offlineDate           CHAR(15)        NOT NULL,
  CONSTRAINT jivePresence_pk PRIMARY KEY (username)
);

UPDATE jiveVersion set version=11 where name = 'openfire';
