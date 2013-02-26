-- Change BIGINT columns to match INTEGER from Openfire
ALTER TABLE gatewayRegistration ALTER COLUMN registrationID TYPE INTEGER;
ALTER TABLE gatewayPseudoRoster ALTER COLUMN registrationID TYPE INTEGER;

-- Change DATE columns to match CHAR(15) from Openfire
ALTER TABLE gatewayRegistration ALTER COLUMN registrationDate TYPE CHAR(15);
ALTER TABLE gatewayRegistration ALTER COLUMN lastLogin TYPE CHAR(15);
ALTER TABLE gatewayAvatars ALTER COLUMN createDate TYPE CHAR(15);
ALTER TABLE gatewayAvatars ALTER COLUMN lastUpdate TYPE CHAR(15);

-- Update database version
UPDATE jiveVersion SET version = 7 WHERE name = 'gateway';
