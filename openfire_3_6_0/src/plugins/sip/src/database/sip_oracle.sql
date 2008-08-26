-- $Revision$
-- $Date$

INSERT INTO ofVersion (name, version) VALUES ('sip', 2);

create table ofSipUser (
	username VARCHAR2(255) NOT NULL,
	sipUsername VARCHAR2(255),
	sipAuthuser VARCHAR2(255),
	sipDisplayName VARCHAR2(255),
	sipPassword VARCHAR2(255),
	sipServer VARCHAR2(255),
	stunServer VARCHAR2(255),
	stunPort VARCHAR2(255),
	useStun INTEGER,
	voicemail VARCHAR2(255),
	enabled INTEGER,
	status VARCHAR2(255),
	outboundproxy VARCHAR(255) NULL,
	promptCredentials INT NULL,
    CONSTRAINT ofSipUser_pk PRIMARY KEY (username)
);

create table ofSipPhoneLog (
	username VARCHAR2(255),
	addressFrom VARCHAR2(255),
	addressTo VARCHAR2(255),
	datetime LONG,
	duration INTEGER,
	callType VARCHAR2(20)
 );