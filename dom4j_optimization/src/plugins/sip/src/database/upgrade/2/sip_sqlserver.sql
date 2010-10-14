/* Rename sipUser to ofSipUser */
ALTER TABLE sipUser DROP CONSTRAINT sipUser_pk;
sp_rename 'sipUser', 'ofSipUser';
ALTER TABLE ofSipUser ADD CONSTRAINT ofSipUser_pk PRIMARY KEY (username);

/* Rename sipPhoneLog to ofSipPhoneLog */
sp_rename 'sipPhoneLog', 'ofSipPhoneLog';

UPDATE ofVersion set version=2 where name='sip';
