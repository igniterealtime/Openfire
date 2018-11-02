# Rename sipUser to ofSipUser
RENAME TABLE sipUser TO ofSipUser;

# Rename sipPhoneLog to ofSipPhoneLog
RENAME TABLE sipPhoneLog TO ofSipPhoneLog;

UPDATE ofVersion set version=2 where name='sip';
