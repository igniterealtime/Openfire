# Add ofFastToken table for FAST (XEP-0484) authentication token storage.

CREATE TABLE ofFastToken (
  username              VARCHAR(64)     NOT NULL,
  mechanism             VARCHAR(32)     NOT NULL,
  clientID              VARCHAR(64)     NOT NULL,
  tokenSlot             VARCHAR(1)      NOT NULL,
  replayCounter         BIGINT          NOT NULL,
  encryptedToken        VARCHAR(255)    NOT NULL,
  expiry                CHAR(15)        NOT NULL,
  PRIMARY KEY (username, mechanism, clientID, tokenSlot)
);
CREATE INDEX ofFastToken_exp_idx ON ofFastToken (expiry);

UPDATE ofVersion SET version = 41 WHERE name = 'openfire';
