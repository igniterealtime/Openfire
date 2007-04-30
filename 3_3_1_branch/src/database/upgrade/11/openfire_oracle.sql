-- $Revision:  $
-- $Date:  $

CREATE TABLE jivePresence (
  username              VARCHAR2(64)    NOT NULL,
  offlinePresence       LONG,
  offlineDate           CHAR(15)        NOT NULL,
  CONSTRAINT jivePresence_pk PRIMARY KEY (username)
);

UPDATE jiveVersion set version=11 where name = 'openfire';
