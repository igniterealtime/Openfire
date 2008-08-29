# $Revision$
# $Date$

INSERT INTO ofVersion (name, version) VALUES ('monitoring', 0);

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
   conversationID    BIGINT           NOT NULL,
   fromJID           VARCHAR(255)     NOT NULL,
   toJID             VARCHAR(255)     NOT NULL,
   sentDate          BIGINT           NOT NULL,
   body              TEXT,
   INDEX entMsgArchive_con_idx (conversationID)
);

CREATE TABLE ofRRDs (
   id            VARCHAR(100)         NOT NULL,
   updatedDate   BIGINT               NOT NULL,
   bytes         MEDIUMBLOB           NULL,
   PRIMARY KEY  (id)
);

