-- Add ofFastToken table for FAST (XEP-0484) authentication token storage.

CREATE TABLE ofFastToken
(
    username          VARCHAR(64)  NOT NULL,
    mechanism         VARCHAR(32)  NOT NULL,
    tokenHash         VARCHAR(64)  NOT NULL,
    expiry            VARCHAR(35)  NOT NULL,
    CONSTRAINT ofFastToken_pk PRIMARY KEY (username, mechanism)
);

UPDATE ofVersion SET version = 41 WHERE name = 'openfire';
