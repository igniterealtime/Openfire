-- create table jiveSecurityAuditLog
CREATE TABLE jiveSecurityAuditLog (
  msgID                 INTEGER         NOT NULL,
  username              VARCHAR2(64)    NOT NULL,
  entryStamp            INTEGER         NOT NULL,
  summary               VARCHAR2(255)   NOT NULL,
  node                  VARCHAR2(255)   NOT NULL,
  details               VARCHAR2(4000),
  CONSTRAINT jiveSecAuditLog_pk PRIMARY KEY (msgID)
);
CREATE INDEX jiveSecAuditLog_tstamp_idx ON jiveSecurityAuditLog (entryStamp);
CREATE INDEX jiveSecAuditLog_uname_idx ON jiveSecurityAuditLog (username);

UPDATE jiveVersion set version=16 where name = 'openfire';

commit;
