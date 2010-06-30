-- Rename sipUser to ofSipUser
ALTER TABLE sipUser DROP CONSTRAINT sipUser_pk;
ALTER TABLE sipUser RENAME TO ofSipUser;
ALTER TABLE ofSipUser ADD CONSTRAINT ofSipUser_pk PRIMARY KEY (username);

-- Rename sipPhoneLog to ofSipPhoneLog
ALTER TABLE sipPhoneLog RENAME TO ofSipPhoneLog;

UPDATE ofVersion set version=2 where name='sip';
