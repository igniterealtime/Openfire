CREATE TABLE ofPfRules(
   id           INT          NOT NULL,
   ruleorder    INT      ,
   type         varchar2(255)     ,
   tojid       varchar2(255)    ,
   fromjid     varchar2(255)    ,
   rulef         varchar2(255)   ,
   disabled     INT,
   log          INT,
   description  varchar2(255),
   sourcetype   varchar2(255),
   desttype     varchar2(255),
  CONSTRAINT ofPfRules_pk PRIMARY KEY (id)
);

CREATE SEQUENCE ofPfRules_seq
START WITH 1
INCREMENT BY 1
MINVALUE 1
NOCACHE
NOCYCLE
NOORDER;

CREATE OR REPLACE TRIGGER ofPfRules_pkcreate BEFORE INSERT ON ofPfRules FOR EACH ROW WHEN ( NEW.id IS NULL ) BEGIN SELECT ofPfRules_seq.NEXTVAL INTO :NEW.id FROM DUAL; END ofPfRules_pkcreate;

INSERT INTO ofVersion (name,version) values ('packetfilter',2);
