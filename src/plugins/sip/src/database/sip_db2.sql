-- $Revision$
-- $Date$

INSERT INTO ofVersion (name, version) VALUES ('sip', 2);

create table ofSipUser (
	username VARCHAR(255) NOT NULL,
	sipUsername VARCHAR(255),
	sipAuthuser VARCHAR(255),
	sipDisplayName VARCHAR(255),
	sipPassword VARCHAR(255),
	sipServer VARCHAR(255),
	stunServer VARCHAR(255),
	stunPort VARCHAR(255),
	useStun INTEGER,
	voicemail VARCHAR(255),
	enabled INTEGER,
	status VARCHAR(255),
	outboundproxy VARCHAR(255),
	promptCredentials INTEGER,
    CONSTRAINT ofSipUser_pk PRIMARY KEY(username)
);

create table ofSipPhoneLog (
	username VARCHAR(255),
	addressFrom VARCHAR(255),
	addressTo VARCHAR(255),
	datetime BIGINT,
	duration INTEGER,
	callType VARCHAR(20)
 );