
INSERT INTO ofVersion (name, version) VALUES ('sip', 2);

create table ofSipUser (
    username varchar(255) not null unique,
    sipUsername varchar(255),
    sipAuthuser varchar(255),
    sipDisplayName varchar(255),
    sipPassword varchar(255),
    sipServer varchar(255),
    stunServer varchar(255),
    stunPort varchar(255),
    useStun int,
    voicemail varchar(255),
    enabled int,
    status varchar(255),
    outboundproxy VARCHAR(255) NULL,
    promptCredentials INTEGER NULL,
    PRIMARY KEY (username)
);

create table ofSipPhoneLog (
    username varchar(255),
    addressFrom varchar(255),
    addressTo varchar(255),
    datetime bigint,
    duration int,
    callType varchar(20)
 );
