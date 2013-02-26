-- Rename gatewayRegistration to ofGatewayRegistration
DROP INDEX gatewayReg_jid_idx;
DROP INDEX gatewayReg_type_idx;
ALTER TABLE gatewayRegistration DROP CONSTRAINT gatewayReg_pk;
ALTER TABLE gatewayRegistration RENAME TO ofGatewayRegistration;
ALTER TABLE ofGatewayRegistration ADD CONSTRAINT ofGatewayRegistration_pk PRIMARY KEY (registrationID);
CREATE INDEX ofGatewayRegistration_jid_idx ON ofGatewayRegistration (jid);
CREATE INDEX ofGatewayRegistration_type_idx ON ofGatewayRegistration (transportType);

-- Rename gatewayPseudoRoster to ofGatewayPseudoRoster
DROP INDEX gatewayPsRs_regid_idx;
DROP INDEX gatewayPsRs_uname_idx;
ALTER TABLE gatewayPseudoRoster RENAME TO ofGatewayPseudoRoster;
CREATE INDEX ofGatewayPseudoRoster_regid_idx ON ofGatewayPseudoRoster (registrationID);
CREATE INDEX ofGatewayPseudoRoster_uname_idx ON ofGatewayPseudoRoster (username);

-- Rename gatewayRestrictions to ofGatewayRestrictions
DROP INDEX gatewayRstr_ttype_idx;
DROP INDEX gatewayRstr_uname_idx;
ALTER TABLE gatewayRestrictions RENAME TO ofGatewayRestrictions;
CREATE INDEX ofGatewayRestrictions_ttype_idx ON ofGatewayRestrictions (transportType);
CREATE INDEX ofGatewayRestrictions_uname_idx ON ofGatewayRestrictions (username);

-- Rename gatewayAvatars to ofGatewayAvatars
DROP INDEX gatewayAvtr_jid_idx;
ALTER TABLE gatewayAvatars RENAME TO ofGatewayAvatars;
CREATE INDEX ofGatewayAvatars_jid_idx ON ofGatewayAvatars (jid);

-- Rename gatewayVCards to ofGatewayVCards
DROP INDEX gatewayVCrd_jid_idx;
ALTER TABLE gatewayVCards RENAME TO ofGatewayVCards;
CREATE INDEX ofGatewayVCards_jid_idx ON ofGatewayVCards (jid);

-- Update database version
UPDATE ofVersion SET version = 8 WHERE name = 'gateway';
