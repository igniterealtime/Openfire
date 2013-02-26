# Rename gatewayRegistration to ofGatewayRegistration
ALTER TABLE gatewayRegistration DROP INDEX gatewayReg_jid_idx;
ALTER TABLE gatewayRegistration DROP INDEX gatewayReg_type_idx;
RENAME TABLE gatewayRegistration TO ofGatewayRegistration;
ALTER TABLE ofGatewayRegistration ADD INDEX ofGatewayRegistration_jid_idx(jid);
ALTER TABLE ofGatewayRegistration ADD INDEX ofGatewayRegistration_type_idx(transportType);

# Rename gatewayPseudoRoster to ofGatewayPseudoRoster
ALTER TABLE gatewayPseudoRoster DROP INDEX gatewayPsRs_regid_idx;
ALTER TABLE gatewayPseudoRoster DROP INDEX gatewayPsRs_uname_idx;
RENAME TABLE gatewayPseudoRoster TO ofGatewayPseudoRoster;
ALTER TABLE ofGatewayPseudoRoster ADD INDEX ofGatewayPseudoRoster_regid_idx(registrationID);
ALTER TABLE ofGatewayPseudoRoster ADD INDEX ofGatewayPseudoRoster_uname_idx(username);

# Rename gatewayRestrictions to ofGatewayRestrictions
ALTER TABLE gatewayRestrictions DROP INDEX gatewayRstr_ttype_idx;
ALTER TABLE gatewayRestrictions DROP INDEX gatewayRstr_uname_idx;
RENAME TABLE gatewayRestrictions TO ofGatewayRestrictions;
ALTER TABLE ofGatewayRestrictions ADD INDEX ofGatewayRestrictions_ttype_idx(transportType);
ALTER TABLE ofGatewayRestrictions ADD INDEX ofGatewayRestrictions_uname_idx(username);

# Rename gatewayAvatars to ofGatewayAvatars
RENAME TABLE gatewayAvatars TO ofGatewayAvatars;

# Rename gatewayVCards to ofGatewayVCards
RENAME TABLE gatewayVCards TO ofGatewayVCards;

# Update database version
UPDATE ofVersion SET version = 8 WHERE name = 'gateway';
