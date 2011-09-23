-- Change ofGatewayAvatars.imageData to CLOB (rom BLOB)
ALTER TABLE ofGatewayAvatars MODIFY imageData CLOB;

-- Change ofGatewayVCards.value to CLOB (from BLOB)
ALTER TABLE ofGatewayVCards MODIFY value CLOB;

-- Update database version
UPDATE ofVersion SET version = 9 WHERE name = 'gateway';

commit;
