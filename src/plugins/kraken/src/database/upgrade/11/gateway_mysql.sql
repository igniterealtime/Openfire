# Update ICQ connection hostname
UPDATE ofProperty SET propValue = 'login.icq.com' WHERE name = 'plugin.gateway.icq.connecthost';

# Update database version
UPDATE ofVersion SET version = 11 WHERE name = 'gateway';
