// Update vcards table to proper value date type
ALTER TABLE gatewayVCards ALTER COLUMN value LONGVARCHAR NOT NULL;

// Update avatar table to proper imageData data type
ALTER TABLE gatewayAvatars ALTER COLUMN imageData LONGVARCHAR NOT NULL;

// Update database version
UPDATE jiveVersion SET version = 5 WHERE name = 'gateway';
