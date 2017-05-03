
INSERT INTO ofVersion (name, version) VALUES ('monitoring', 4);

CREATE TABLE ofConversation (
  conversationID        BIGINT        NOT NULL,
  room                  VARCHAR(255)  NULL,
  isExternal            TINYINT       NOT NULL,
  startDate             BIGINT        NOT NULL,
  lastActivity          BIGINT        NOT NULL,
  messageCount          INT           NOT NULL,
  PRIMARY KEY (conversationID),
  INDEX ofConversation_ext_idx (isExternal),
  INDEX ofConversation_start_idx (startDate),
  INDEX ofConversation_last_idx (lastActivity)
);

CREATE TABLE ofConParticipant (
  conversationID       BIGINT         NOT NULL,
  joinedDate           BIGINT         NOT NULL,
  leftDate             BIGINT         NULL,
  bareJID              VARCHAR(200)   NOT NULL,
  jidResource          VARCHAR(100)   NOT NULL,
  nickname             VARCHAR(255)   NULL,
  INDEX ofConParticipant_conv_idx (conversationID, bareJID, jidResource, joinedDate),
  INDEX ofConParticipant_jid_idx (bareJID)
);

CREATE TABLE ofMessageArchive (
   messageID		 BIGINT			  NULL,
   conversationID    BIGINT           NOT NULL,
   fromJID           VARCHAR(255)     NOT NULL,
   fromJIDResource   VARCHAR(100)     NULL,
   toJID             VARCHAR(255)     NOT NULL,
   toJIDResource     VARCHAR(100)     NULL,
   sentDate          BIGINT           NOT NULL,
   stanza			 TEXT			  NULL,
   body              TEXT,
   INDEX ofMessageArchive_con_idx (conversationID),
   INDEX ofMessageArchive_fromjid_idx (fromJID),
   INDEX ofMessageArchive_tojid_idx (toJID)
);

CREATE TABLE ofRRDs (
   id            VARCHAR(100)         NOT NULL,
   updatedDate   BIGINT               NOT NULL,
   bytes         MEDIUMBLOB           NULL,
   PRIMARY KEY  (id)
);

