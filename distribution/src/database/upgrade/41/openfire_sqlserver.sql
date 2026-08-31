/* Add ofFastToken table for FAST (XEP-0484) authentication token storage. */

CREATE TABLE ofFastToken (
  username              NVARCHAR(64)    NOT NULL,
  mechanism             VARCHAR(32)     NOT NULL,
  clientID              VARCHAR(64)     NOT NULL,
  tokenSlot             VARCHAR(1)      NOT NULL,
  replayCounter         BIGINT          NOT NULL,
  encryptedToken        VARCHAR(255)    NOT NULL,
  expiry                VARCHAR(35)     NOT NULL,
  CONSTRAINT ofFastToken_pk PRIMARY KEY (username, mechanism, clientID, tokenSlot)
);

UPDATE ofVersion SET version = 41 WHERE name = 'openfire';
