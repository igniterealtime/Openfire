-- $Revision:  $
-- $Date:  $

CREATE TABLE jiveSASLAuthorized (
  username          VARCHAR(64)   NOT NULL,
  principal         VARCHAR(4000) NOT NULL,
  CONSTRAINT jiveSASLAuthoirzed_pk PRIMARY KEY (username, principal)
);

UPDATE jiveVersion set version=10 where name = 'openfire';
