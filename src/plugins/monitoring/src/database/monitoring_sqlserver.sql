
INSERT INTO ofVersion (name, version) VALUES ('monitoring', 4);

CREATE TABLE ofConversation (
  conversationID        BIGINT         NOT NULL,
  room                  NVARCHAR(1024) NULL,
  isExternal            TINYINT        NOT NULL,
  startDate             BIGINT         NOT NULL,
  lastActivity          BIGINT         NOT NULL,
  messageCount          INT            NOT NULL,
  CONSTRAINT ofConversation_pk PRIMARY KEY (conversationID)
);
CREATE INDEX ofConversation_ext_idx   ON ofConversation (isExternal);
CREATE INDEX ofConversation_start_idx ON ofConversation (startDate);
CREATE INDEX ofConversation_last_idx  ON ofConversation (lastActivity);

CREATE TABLE ofConParticipant (
  conversationID       BIGINT         NOT NULL,
  joinedDate           BIGINT         NOT NULL,
  leftDate             BIGINT         NULL,
  bareJID              NVARCHAR(255)  NOT NULL,
  jidResource          NVARCHAR(255)  NOT NULL,
  nickname             NVARCHAR(255)  NULL
);
CREATE INDEX ofConParticipant_conv_idx ON ofConParticipant (conversationID, bareJID, jidResource, joinedDate);
CREATE INDEX ofConParticipant_jid_idx ON ofConParticipant (bareJID);

CREATE TABLE ofMessageArchive (
   messageID		 BIGINT			 NULL,
   conversationID    BIGINT          NOT NULL,
   fromJID           NVARCHAR(1024)  NOT NULL,
   fromJIDResource   NVARCHAR(1024)  NULL,
   toJID             NVARCHAR(1024)  NOT NULL,
   toJIDResource     NVARCHAR(1024)  NULL,
   sentDate          BIGINT          NOT NULL,
   stanza			 NVARCHAR(MAX)   NULL,
   body              NVARCHAR(MAX)
);
CREATE INDEX ofMessageArchive_con_idx ON ofMessageArchive (conversationID);
CREATE INDEX ofMessageArchive_fromjid_idx ON ofMessageArchive (fromJID);
CREATE INDEX ofMessageArchive_tojid_idx ON ofMessageArchive (toJID);

CREATE TABLE ofRRDs (
   id            NVARCHAR(100)        NOT NULL,
   updatedDate   BIGINT               NOT NULL,
   bytes         IMAGE                NULL,
   CONSTRAINT ofRRDs_pk PRIMARY KEY (id)
);

