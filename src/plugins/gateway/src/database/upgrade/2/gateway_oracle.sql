-- Add restrictions table
CREATE TABLE gatewayRestrictions (
   transportType     VARCHAR2(15)    NOT NULL,
   username          VARCHAR2(255),
   groupname         VARCHAR2(50)
);
CREATE INDEX gatewayRstr_ttype_idx ON gatewayRestrictions (transportType);
CREATE INDEX gatewayRstr_uname_idx ON gatewayRestrictions (username);

-- Update database version
UPDATE jiveVersion SET version = 2 WHERE name = 'gateway';

commit;
