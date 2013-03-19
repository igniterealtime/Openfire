/* Rename gatewayRegistration to ofGatewayRegistration */
DROP INDEX gatewayRegistration.gatewayReg_jid_idx;
DROP INDEX gatewayRegistration.gatewayReg_type_idx;
ALTER TABLE gatewayRegistration DROP CONSTRAINT gatewayReg_pk;
sp_rename 'gatewayRegistration', 'ofGatewayRegistration';
ALTER TABLE ofGatewayRegistration ADD CONSTRAINT ofGatewayRegistration_pk PRIMARY KEY (registrationID);
CREATE INDEX ofGatewayRegistration_jid_idx ON ofGatewayRegistration (jid);
CREATE INDEX ofGatewayRegistration_type_idx ON ofGatewayRegistration (transportType);

/* Rename gatewayPseudoRoster to ofGatewayPseudoRoster */
DROP INDEX gatewayPseudoRoster.gatewayPsRs_regid_idx;
DROP INDEX gatewayPseudoRoster.gatewayPsRs_uname_idx;
sp_rename 'gatewayPseudoRoster', 'ofGatewayPseudoRoster';
CREATE INDEX ofGatewayPseudoRoster_regid_idx ON ofGatewayPseudoRoster (registrationID);
CREATE INDEX ofGatewayPseudoRoster_uname_idx ON ofGatewayPseudoRoster (username);

/* Rename gatewayRestrictions to ofGatewayRestrictions */
DROP INDEX gatewayRestrictions.gatewayRstr_ttype_idx;
DROP INDEX gatewayRestrictions.gatewayRstr_uname_idx;
sp_rename 'gatewayRestrictions', 'ofGatewayRestrictions';
CREATE INDEX ofGatewayRestrictions_ttype_idx ON ofGatewayRestrictions (transportType);
CREATE INDEX ofGatewayRestrictions_uname_idx ON ofGatewayRestrictions (username);

/* Rename gatewayAvatars to ofGatewayAvatars */
DROP INDEX gatewayAvatars.gatewayAvtr_jid_idx;
sp_rename 'gatewayAvatars', 'ofGatewayAvatars';
CREATE INDEX ofGatewayAvatars_jid_idx ON ofGatewayAvatars (jid);

/* Rename gatewayVCards to ofGatewayVCards */
DROP INDEX gatewayVCards.gatewayVCrd_jid_idx;
sp_rename 'gatewayVCards', 'ofGatewayVCards';
CREATE INDEX ofGatewayVCards_jid_idx ON ofGatewayVCards (jid);

/* Update database version */
UPDATE ofVersion SET version = 8 WHERE name = 'gateway';
