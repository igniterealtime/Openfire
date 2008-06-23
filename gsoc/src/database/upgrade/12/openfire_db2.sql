-- $Revision:  $
-- $Date:  $

CREATE TABLE mucConversationLog2 (
  roomID              INTEGER       NOT NULL,
  sender              VARCHAR(2000) NOT NULL,
  nickname            VARCHAR(255),
  logTime             CHAR(15)      NOT NULL,
  subject             VARCHAR(255),
  body                CLOB
);

INSERT INTO mucConversationLog2 (roomID, sender, nickname, logTime, subject, body)
SELECT roomID, sender, nickname, time, subject, body FROM mucConversationLog;

DROP TABLE mucConversationLog;
RENAME TABLE mucConversationLog2 TO mucConversationLog;
CREATE INDEX mucLog_logtime_idx ON mucConversationLog (logTime);

UPDATE jiveVersion set version=12 where name = 'openfire';