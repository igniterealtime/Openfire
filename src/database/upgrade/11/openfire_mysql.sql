
CREATE TABLE jivePresence (
  username              VARCHAR(64)     NOT NULL,
  offlinePresence       TEXT,
  offlineDate           CHAR(15)     NOT NULL,
  PRIMARY KEY (username)
);

UPDATE jiveVersion set version=11 where name = 'openfire';
