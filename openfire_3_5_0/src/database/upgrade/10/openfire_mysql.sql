# $Revision:  $
# $Date:  $

CREATE TABLE jiveSASLAuthorized (
  username            VARCHAR(64)   NOT NULL,
  principal           TEXT          NOT NULL,
  PRIMARY KEY (username, principal(200))
);

UPDATE jiveVersion set version=10 where name = 'openfire';
