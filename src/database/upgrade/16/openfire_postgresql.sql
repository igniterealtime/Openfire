-- create table jiveSecurityAuditLog
CREATE TABLE jiveSecurityAuditLog (
  msgID                 INTEGER         NOT NULL,
  username              VARCHAR(64)     NOT NULL,
  entryStamp            BIGINT          NOT NULL,
  summary               VARCHAR(255)    NOT NULL,
  node                  VARCHAR(255)    NOT NULL,
  details               TEXT,
  CONSTRAINT jiveSecAuditLog_pk PRIMARY KEY (msgID)
);
CREATE INDEX jiveSecAuditLog_tstamp_idx ON jiveSecurityAuditLog (entryStamp);
CREATE INDEX jiveSecAuditLog_uname_idx ON jiveSecurityAuditLog (username);

UPDATE jiveVersion set version=16 where name = 'openfire';
