INSERT INTO ofSipUser
(username, sipUsername, sipAuthuser, sipDisplayName, sipPassword, sipServer, stunServer, stunPort, useStun, voicemail, enabled, status, outboundproxy, promptCredentials)
SELECT username, sipUsername, sipAuthuser, sipDisplayName, sipPassword, sipServer, stunServer, stunPort, useStun, voicemail, enabled, status, outboundproxy, promptCredentials
FROM entSipUser;

INSERT INTO ofSipPhoneLog
(username, addressFrom, addressTo, datetime, duration, callType)
SELECT username, addressFrom, addressTo, datetime, duration, callType
FROM entPhoneLog;