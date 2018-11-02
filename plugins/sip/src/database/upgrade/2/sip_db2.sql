-- Rename sipUser to ofSipUser
ALTER TABLE sipUser DROP CONSTRAINT sipUser_pk;
RENAME sipUser TO ofSipUser;
ALTER TABLE ofSipUser ADD CONSTRAINT ofSipUser_pk PRIMARY KEY(username);

-- Rename sipPhoneLog to ofSipPhoneLog
RENAME sipPhoneLog TO ofSipPhoneLog;

UPDATE ofVersion set version=2 where name='sip';
