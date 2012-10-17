-- $Revision$
-- $Date$

INSERT INTO ofVersion (name, version) VALUES ('monitoring', 1);

CREATE TABLE ofConversation (
  conversationID        INTEGER        NOT NULL,
  room                  VARCHAR2(1024) NULL,
  isExternal            NUMBER(2)      NOT NULL,
  startDate             INTEGER        NOT NULL,
  lastActivity          INTEGER        NOT NULL,
  messageCount          INT            NOT NULL,
  CONSTRAINT ofConversation_pk PRIMARY KEY (conversationID)
);
CREATE INDEX ofConversation_ext_idx   ON ofConversation (isExternal);
CREATE INDEX ofConversation_start_idx ON ofConversation (startDate);
CREATE INDEX ofConversation_last_idx  ON ofConversation (lastActivity);

CREATE TABLE ofConParticipant (
  conversationID       INTEGER        NOT NULL,
  joinedDate           INTEGER        NOT NULL,
  leftDate             INTEGER        NULL,
  bareJID              VARCHAR2(255)  NOT NULL,
  jidResource          VARCHAR2(255)  NOT NULL,
  nickname             VARCHAR2(255)  NULL
);
CREATE INDEX ofConParticipant_conv_idx ON ofConParticipant (conversationID, bareJID, jidResource, joinedDate);
CREATE INDEX ofConParticipant_jid_idx ON ofConParticipant (bareJID);

CREATE TABLE ofMessageArchive (
   conversationID    INTEGER          NOT NULL,
   fromJID           VARCHAR2(1024)   NOT NULL,
   fromJIDResource   VARCHAR2(255)    NULL,
   toJID             VARCHAR2(1024)   NOT NULL,
   toJIDResource   VARCHAR2(255)      NULL,
   sentDate          INTEGER          NOT NULL,
   body              LONG
);
CREATE INDEX ofMessageArchive_con_idx ON ofMessageArchive (conversationID);

CREATE TABLE ofRRDs (
   id            VARCHAR2(100)        NOT NULL,
   updatedDate   INTEGER              NOT NULL,
   bytes         BLOB                 NULL,
   CONSTRAINT ofRRDs_pk PRIMARY KEY (id)
);

