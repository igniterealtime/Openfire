-- $Revision$
-- $Date$

INSERT INTO jiveVersion (name, version) VALUES ('sip', 1);

create table sipUser (
	username varchar(255) not null,
	sipUsername varchar(255),
	sipAuthuser varchar(255),
	sipDisplayName varchar(255),
	sipPassword varchar(255),
	sipServer varchar(255),
	stunServer varchar(255),
	stunPort varchar(255),
	useStun INTEGER,
	voicemail varchar(255),
	enabled INTEGER,
	status varchar(255),
	outboundproxy VARCHAR(255) NULL,
	promptCredentials INTEGER NULL,
    PRIMARY KEY (username)
);

create table sipPhoneLog (
	username varchar(255),
	addressFrom varchar(255),
	addressTo varchar(255),
	datetime INTEGER,
	duration INTEGER,
	callType varchar(20)
 );