-- Rename sipUser to ofSipUser
ALTER TABLE sipUser RENAME TO ofSipUser;

-- Rename sipPhoneLog to ofSipPhoneLog
ALTER TABLE sipPhoneLog RENAME TO ofSipPhoneLog;

UPDATE ofVersion set version=2 where name='sip';
