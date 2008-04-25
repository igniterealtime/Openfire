DROP TABLE pfRules;
CREATE TABLE pfRules (
   id           BIGINT   IDENTITY,
   ruleorder    BIGINT      ,
   type         varchar(255)     ,
   tojid       varchar(255)    ,
   fromjid     varchar(255)    ,
   rulef         varchar(255)   ,
   disabled     boolean,
   log          boolean,
   description  varchar(255),
   sourcetype   varchar(255),
   desttype     varchar(255),
   CONSTRAINT  pfRules_pk PRIMARY KEY (id)
);
CREATE INDEX pfRules_idx ON pfRules(id);
UPDATE jiveVersion set version=1 where name='packetfilter';