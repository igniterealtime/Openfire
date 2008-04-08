/* $Revision$   */
/* $Date$       */

INSERT INTO jiveVersion (name, version) VALUES ('sip', 1);

create table sipUser (
	username NVARCHAR(255) not null,
	sipUsername NVARCHAR(255),
	sipAuthuser NVARCHAR(255),
	sipDisplayname NVARCHAR(255),
	sipPassword NVARCHAR(255),
	sipServer NVARCHAR(255),
	stunServer NVARCHAR(255),
	stunPort NVARCHAR(255),
	useStun int,
	voicemail NVARCHAR(255),
	enabled int,
	status NVARCHAR(255),
	outboundproxy VARCHAR(255) NULL,
	promptCredentials INT NULL,
    CONSTRAINT sipUser_pk PRIMARY KEY (username)
);

create table sipPhoneLog (
	username NVARCHAR(255),
	addressFrom NVARCHAR(255),
	addressTo NVARCHAR(255),
	datetime bigint,
	duration int,
	callType NVARCHAR(20)
 );