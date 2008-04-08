CREATE TABLE pfRules(
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
  CONSTRAINT pfRules_pk PRIMARY KEY (id)
);

CREATE SEQUENCE pfRules_seq
START WITH 1
INCREMENT BY 1
MINVALUE 1
NOCACHE
NOCYCLE
NOORDER;

CREATE OR REPLACE TRIGGER pfRules_pkcreate BEFORE INSERT ON pfRules FOR EACH ROW WHEN ( NEW.id IS NULL ) BEGIN SELECT pfRules_seq.NEXTVAL INTO :NEW.id FROM DUAL; END pfRules_pkcreate;

INSERT INTO jiveVersion (name,version) values ('packetfilter',1);
