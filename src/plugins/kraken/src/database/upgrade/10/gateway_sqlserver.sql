/* Rename sip gateway to simple */
UPDATE ofGatewayRegistration SET transportType = 'simple' WHERE transportType = 'sip';
UPDATE ofGatewayRestrictions SET transportType = 'simple' WHERE transportType = 'sip';

/* Change system properties for sip gateway to simple */
UPDATE ofProperty SET name = REPLACE(name, 'plugin.gateway.sip.','plugin.gateway.simple.');

/* Change roster JIDs with sip. in them to simple. */
UPDATE ofRoster SET jid = REPLACE(jid, 'sip.','simple.') WHERE jid NOT LIKE '%@%';
UPDATE ofRoster SET jid = REPLACE(jid, 'sip.','simple.') WHERE jid LIKE '%@sip.%';

/* Update database version */
UPDATE ofVersion SET version = 10 WHERE name = 'gateway';
