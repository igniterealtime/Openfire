# create jiveUserFlag table
CREATE TABLE jiveUserFlag (
  username              VARCHAR(64)     NOT NULL,
  name                  VARCHAR(100)    NOT NULL,
  startTime             CHAR(15),
  endTime               CHAR(15),
  PRIMARY KEY (username, name),
  INDEX jiveUser_sTime_idx (startTime),
  INDEX jiveUser_eTime_idx (endTime)
);

UPDATE jiveVersion set version=15 where name = 'openfire';
