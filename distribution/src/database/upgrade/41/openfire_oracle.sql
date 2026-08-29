-- Add ofFastToken table for FAST (XEP-0484) authentication token storage.

CREATE TABLE ofFastToken (
  username              VARCHAR2(64)    NOT NULL,
  mechanism             VARCHAR2(32)    NOT NULL,
  clientID             VARCHAR2(64)     NOT NULL,
  tokenSlot             VARCHAR2(1)      NOT NULL,
  replayCounter         NUMBER(19)       NOT NULL,
  tokenHash             VARCHAR2(128)    NOT NULL,
  expiry                VARCHAR2(35)    NOT NULL,
  CONSTRAINT ofFastToken_pk PRIMARY KEY (username, mechanism, clientID, tokenSlot)
);

UPDATE ofVersion SET version = 41 WHERE name = 'openfire';

commit;
