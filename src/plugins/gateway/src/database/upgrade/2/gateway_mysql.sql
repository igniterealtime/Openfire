# Add restrictions table
CREATE TABLE gatewayRestrictions (
   transportType     VARCHAR(15)    NOT NULL,
   username          VARCHAR(255),
   groupname         VARCHAR(50),
   INDEX gatewayRstr_ttype_idx(transportType)
);

# Update database version
UPDATE jiveVersion SET version = 2 WHERE name = 'gateway';
