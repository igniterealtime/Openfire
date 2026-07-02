-- Move SCRAM credentials from ofUser columns into a dedicated, mechanism-keyed table.
--
-- This first step (schema version 39) creates the new table and copies existing SHA-1 credentials into it.
-- The old columns on ofUser are left in place for now, and are dropped in the next step (version 40). Splitting
-- create-and-copy (39) from drop (40) means that a half-applied upgrade never loses credential data.

CREATE TABLE ofUserScram (
  username              VARCHAR(64)     NOT NULL,
  mechanism             VARCHAR(32)     NOT NULL,
  storedKey             VARCHAR(255),
  serverKey             VARCHAR(255),
  salt                  VARCHAR(255),
  iterations            INTEGER         NOT NULL,
  PRIMARY KEY (username, mechanism)
);

INSERT INTO ofUserScram (username, mechanism, iterations, salt, storedKey, serverKey)
SELECT username, 'SCRAM-SHA-1', iterations, salt, storedKey, serverKey
  FROM ofUser
 WHERE storedKey IS NOT NULL
   AND serverKey IS NOT NULL
   AND salt IS NOT NULL;

UPDATE ofVersion SET version = 39 WHERE name = 'openfire';
