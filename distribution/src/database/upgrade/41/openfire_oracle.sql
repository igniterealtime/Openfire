-- Add ofFastToken table for FAST (XEP-0484) authentication token storage.

CREATE TABLE ofFastToken (
  username              VARCHAR2(64)    NOT NULL,
  mechanism             VARCHAR2(32)    NOT NULL,
  clientID              VARCHAR2(64)    NOT NULL,
  tokenSlot             VARCHAR2(1)     NOT NULL,
  replayCounter         NUMBER(19)      NOT NULL,
  encryptedToken        VARCHAR2(255)   NOT NULL,
  expiry                CHAR(15)        NOT NULL,
  CONSTRAINT ofFastToken_pk PRIMARY KEY (username, mechanism, clientID, tokenSlot)
);
CREATE INDEX ofFastToken_exp_idx ON ofFastToken (expiry);

UPDATE ofVersion SET version = 41 WHERE name = 'openfire';

commit;
