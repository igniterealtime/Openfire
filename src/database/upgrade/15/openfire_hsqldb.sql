// create table jiveUserFlag
CREATE TABLE jiveUserFlag (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  startTime             VARCHAR(15),
  endTime               VARCHAR(15),
  CONSTRAINT jiveUserFlag_pk PRIMARY KEY (username, name)
);
CREATE INDEX jiveUserFlag_sTime_idx ON jiveUserFlag (startTime);
CREATE INDEX jiveUserFlag_eTime_idx ON jiveUserFlag (endTime);

UPDATE jiveVersion set version=15 where name = 'openfire';
