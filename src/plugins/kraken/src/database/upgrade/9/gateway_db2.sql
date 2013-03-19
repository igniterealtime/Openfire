-- Change ofGatewayAvatars.imageData to CLOB (rom BLOB)
ALTER TABLE ofGatewayAvatars ALTER COLUMN imageData SET DATA TYPE CLOB;

-- Change ofGatewayVCards.value to CLOB (from BLOB)
ALTER TABLE ofGatewayVCards ALTER COLUMN value SET DATA TYPE CLOB;

-- Update database version
UPDATE ofVersion SET version = 9 WHERE name = 'gateway';
