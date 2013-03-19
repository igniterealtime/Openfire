// $Revision$
// $Date$

INSERT INTO ofVersion (name, version) VALUES ('monitoring', 1);

CREATE TABLE ofConversation (
  conversationID        BIGINT        NOT NULL,
  room                  VARCHAR(1024) NULL,
  isExternal            INT           NOT NULL,
  startDate             BIGINT        NOT NULL,
  lastActivity          BIGINT        NOT NULL,
  messageCount          INT           NOT NULL,
  CONSTRAINT ofConversation_pk PRIMARY KEY (conversationID)
);
CREATE INDEX ofConversation_ext_idx   ON ofConversation (isExternal);
CREATE INDEX ofConversation_start_idx ON ofConversation (startDate);
CREATE INDEX ofConversation_last_idx  ON ofConversation (lastActivity);

CREATE TABLE ofConParticipant (
  conversationID       BIGINT        NOT NULL,
  joinedDate           BIGINT        NOT NULL,
  leftDate             BIGINT        NULL,
  bareJID              VARCHAR(255)  NOT NULL,
  jidResource          VARCHAR(255)  NOT NULL,
  nickname             VARCHAR(255)  NULL
);
CREATE INDEX ofConParticipant_conv_idx ON ofConParticipant (conversationID, bareJID, jidResource, joinedDate);
CREATE INDEX ofConParticipant_jid_idx ON ofConParticipant (bareJID);

CREATE TABLE ofMessageArchive (
   conversationID    BIGINT          NOT NULL,
   fromJID           VARCHAR(1024)   NOT NULL,
   fromJIDResource   VARCHAR(255)    NULL,
   toJID             VARCHAR(1024)   NOT NULL,
   toJIDResource     VARCHAR(255)    NULL,
   sentDate          BIGINT          NOT NULL,
   body              LONGVARCHAR
);
CREATE INDEX ofMessageArchive_con_idx ON ofMessageArchive (conversationID);

CREATE TABLE ofRRDs (
   id            VARCHAR(100)        NOT NULL,
   updatedDate   BIGINT              NOT NULL,
   bytes         VARBINARY           NULL,
   CONSTRAINT ofRRDs_pk PRIMARY KEY (id)
);

