/* Add restrictions table */
CREATE TABLE gatewayRestrictions (
   transportType     NVARCHAR(15)    NOT NULL,
   username          NVARCHAR(255),
   groupname         NVARCHAR(50)
);
CREATE INDEX gatewayRstr_ttype_idx ON gatewayRestrictions (transportType);

/* Update database version */
UPDATE jiveVersion SET version = 2 WHERE name = 'gateway';
