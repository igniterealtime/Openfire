# Update password field size to 1024
ALTER TABLE ofGatewayRegistration MODIFY COLUMN password VARCHAR(1024);

# Update database version
UPDATE ofVersion SET version = 12 WHERE name = 'gateway';
