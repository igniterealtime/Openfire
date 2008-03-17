# change mucRoom's primary key to be referenced around serviceID
ALTER TABLE mucRoom DROP PRIMARY KEY;
ALTER TABLE mucRoom ADD PRIMARY KEY (serviceID,name);

# create table mucService
CREATE TABLE mucService (
  serviceID           BIGINT        NOT NULL,
  subdomain           VARCHAR(255)  NOT NULL,
  description         VARCHAR(255),
  PRIMARY KEY (subdomain),
  INDEX mucService_serviceid_idx (serviceID)
);

# create table mucServiceProp
CREATE TABLE mucServiceProp (
  serviceID           BIGINT        NOT NULL,
  name                VARCHAR(100)  NOT NULL,
  propValue           TEXT          NOT NULL,
  PRIMARY KEY (serviceID, name)
);

# add new indexed column to mucRoom
ALTER TABLE mucRoom ADD COLUMN serviceID BIGINT NOT NULL FIRST;
ALTER TABLE mucRoom ADD INDEX mucRoom_serviceid_idx (serviceID);

# add default entry for conference service and associated jiveID value
INSERT INTO mucService (serviceID, subdomain) VALUES (1, "conference");
INSERT INTO jiveID (idType, id) VALUES (26, 1);

# update all entries in mucRoom to be set to the default conference service
UPDATE mucRoom set serviceID = 1;

# update conference name/desc if there's a custom one set
UPDATE mucService SET mucService.subdomain = ( SELECT jiveProperty.propValue FROM jiveProperty WHERE jiveProperty.name = "xmpp.muc.service" )
  WHERE EXISTS ( SELECT jiveProperty.propValue FROM jiveProperty WHERE jiveProperty.name = "xmpp.muc.service" );
DELETE FROM jiveProperty WHERE name = "xmpp.muc.service";

UPDATE mucService SET mucService.description = ( SELECT jiveProperty.propValue FROM jiveProperty WHERE jiveProperty.name = "muc.service-name" )
  WHERE EXISTS ( SELECT jiveProperty.propValue FROM jiveProperty WHERE jiveProperty.name = "muc.service-name" );
DELETE FROM jiveProperty WHERE name = "muc.service-name";

# transfer all system properties to muc specific properties
INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.canOccupantsChangeSubject",propValue FROM jiveProperty WHERE name = "muc.room.canOccupantsChangeSubject";
DELETE FROM jiveProperty WHERE name = "muc.room.canOccupantsChangeSubject";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.maxUsers",propValue FROM jiveProperty WHERE name = "muc.room.maxUsers";
DELETE FROM jiveProperty WHERE name = "muc.room.maxUsers";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.publicRoom",propValue FROM jiveProperty WHERE name = "muc.room.publicRoom";
DELETE FROM jiveProperty WHERE name = "muc.room.publicRoom";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.persistent",propValue FROM jiveProperty WHERE name = "muc.room.persistent";
DELETE FROM jiveProperty WHERE name = "muc.room.persistent";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.moderated",propValue FROM jiveProperty WHERE name = "muc.room.moderated";
DELETE FROM jiveProperty WHERE name = "muc.room.moderated";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.membersOnly",propValue FROM jiveProperty WHERE name = "muc.room.membersOnly";
DELETE FROM jiveProperty WHERE name = "muc.room.membersOnly";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.canOccupantsInvite",propValue FROM jiveProperty WHERE name = "muc.room.canOccupantsInvite";
DELETE FROM jiveProperty WHERE name = "muc.room.canOccupantsInvite";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.canAnyoneDiscoverJID",propValue FROM jiveProperty WHERE name = "muc.room.canAnyoneDiscoverJID";
DELETE FROM jiveProperty WHERE name = "muc.room.canAnyoneDiscoverJID";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.logEnabled",propValue FROM jiveProperty WHERE name = "muc.room.logEnabled";
DELETE FROM jiveProperty WHERE name = "muc.room.logEnabled";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.loginRestrictedToNickname",propValue FROM jiveProperty WHERE name = "muc.room.loginRestrictedToNickname";
DELETE FROM jiveProperty WHERE name = "muc.room.loginRestrictedToNickname";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.canChangeNickname",propValue FROM jiveProperty WHERE name = "muc.room.canChangeNickname";
DELETE FROM jiveProperty WHERE name = "muc.room.canChangeNickname";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"room.registrationEnabled",propValue FROM jiveProperty WHERE name = "muc.room.registrationEnabled";
DELETE FROM jiveProperty WHERE name = "muc.room.registrationEnabled";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"tasks.user.timeout",propValue FROM jiveProperty WHERE name = "xmpp.muc.tasks.user.timeout";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.tasks.user.timeout";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"tasks.user.idle",propValue FROM jiveProperty WHERE name = "xmpp.muc.tasks.user.idle";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.tasks.user.idle";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"tasks.log.timeout",propValue FROM jiveProperty WHERE name = "xmpp.muc.tasks.log.timeout";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.tasks.log.timeout";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"tasks.log.batchsize",propValue FROM jiveProperty WHERE name = "xmpp.muc.tasks.log.batchsize";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.tasks.log.batchsize";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"sysadmin.jid",propValue FROM jiveProperty WHERE name = "xmpp.muc.sysadmin.jid";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.sysadmin.jid";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"discover.locked",propValue FROM jiveProperty WHERE name = "xmpp.muc.discover.locked";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.discover.locked";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"create.anyone",propValue FROM jiveProperty WHERE name = "xmpp.muc.create.anyone";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.create.anyone";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"create.jid",propValue FROM jiveProperty WHERE name = "xmpp.muc.create.jid";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.create.jid";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"enabled",propValue FROM jiveProperty WHERE name = "xmpp.muc.enabled";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.enabled";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"unload.empty_days",propValue FROM jiveProperty WHERE name = "xmpp.muc.unload.empty_days";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.unload.empty_days";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"discover.locked",propValue FROM jiveProperty WHERE name = "xmpp.muc.discover.locked";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.discover.locked";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"history.maxNumber",propValue FROM jiveProperty WHERE name = "xmpp.muc.history.maxNumber";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.history.maxNumber";

INSERT INTO mucServiceProp(serviceID,name,propValue) SELECT 1,"history.type",propValue FROM jiveProperty WHERE name = "xmpp.muc.history.type";
DELETE FROM jiveProperty WHERE name = "xmpp.muc.history.type";


UPDATE jiveVersion set version=17 where name = 'openfire';
