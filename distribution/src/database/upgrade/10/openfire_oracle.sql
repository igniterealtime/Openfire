
CREATE TABLE jiveSASLAuthorized (
  username            VARCHAR(64)   NOT NULL,
  principal           VARCHAR(3000) NOT NULL,
  CONSTRAINT jiveSASLAuthoirzed_pk PRIMARY KEY (username, principal)
);

UPDATE jiveVersion set version=10 where name = 'openfire';
