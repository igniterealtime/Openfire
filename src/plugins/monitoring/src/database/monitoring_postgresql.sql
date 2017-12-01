
INSERT INTO ofVersion (name, version) VALUES ('monitoring', 4);

CREATE TABLE ofConversation (
  conversationID        INTEGER       NOT NULL,
  room                  VARCHAR(1024) NULL,
  isExternal            SMALLINT      NOT NULL,
  startDate             BIGINT        NOT NULL,
  lastActivity          BIGINT        NOT NULL,
  messageCount          INTEGER       NOT NULL,
  CONSTRAINT ofConversation_pk PRIMARY KEY (conversationID)
);
CREATE INDEX ofConversation_ext_idx   ON ofConversation (isExternal);
CREATE INDEX ofConversation_start_idx ON ofConversation (startDate);
CREATE INDEX ofConversation_last_idx  ON ofConversation (lastActivity);

CREATE TABLE ofConParticipant (
  conversationID       INTEGER       NOT NULL,
  joinedDate           BIGINT        NOT NULL,
  leftDate             BIGINT        NULL,
  bareJID              VARCHAR(255)  NOT NULL,
  jidResource          VARCHAR(255)  NOT NULL,
  nickname             VARCHAR(255)  NULL
);
CREATE INDEX ofConParticipant_conv_idx ON ofConParticipant (conversationID, bareJID, jidResource, joinedDate);
CREATE INDEX ofConParticipant_jid_idx ON ofConParticipant (bareJID);

CREATE TABLE ofMessageArchive (
   messageID		 BIGINT			 NULL,
   conversationID    INTEGER         NOT NULL,
   fromJID           VARCHAR(1024)   NOT NULL,
   fromJIDResource   VARCHAR(1024)   NULL,
   toJID             VARCHAR(1024)   NOT NULL,
   toJIDResource     VARCHAR(1024)   NULL,
   sentDate          BIGINT          NOT NULL,
   stanza			 TEXT			 NULL,
   body              TEXT
);
CREATE INDEX ofMessageArchive_con_idx ON ofMessageArchive (conversationID);
CREATE INDEX ofMessageArchive_fromjid_idx ON ofMessageArchive (fromJID);
CREATE INDEX ofMessageArchive_tojid_idx ON ofMessageArchive (toJID);

CREATE TABLE ofRRDs (
   id            VARCHAR(100)         NOT NULL,
   updatedDate   BIGINT               NOT NULL,
   bytes         bytea                NULL,
   CONSTRAINT ofRRDs_pk PRIMARY KEY (id)
);

