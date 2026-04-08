# create table jiveSecurityAuditLog
CREATE TABLE jiveSecurityAuditLog (
  msgID                 BIGINT          NOT NULL,
  username              VARCHAR(64)     NOT NULL,
  entryStamp            BIGINT          NOT NULL,
  summary               VARCHAR(255)    NOT NULL,
  node                  VARCHAR(255)    NOT NULL,
  details               TEXT,
  PRIMARY KEY (msgID),
  INDEX jiveSecAuditLog_tstamp_idx (entryStamp),
  INDEX jiveSecAuditLog_uname_idx (username)
);

UPDATE jiveVersion set version=16 where name = 'openfire';
