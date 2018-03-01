DROP TABLE pfRules;
CREATE TABLE pfRules (
   id           SERIAL,
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
   CONSTRAINT pfRules_id PRIMARY KEY(id)
);
UPDATE jiveVersion set version=1 where name='packetfilter';
