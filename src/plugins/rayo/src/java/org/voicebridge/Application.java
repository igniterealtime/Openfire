package org.voicebridge;


import java.util.*;
import java.text.ParseException;
import java.net.*;
import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.voip.server.*;
import com.sun.voip.client.*;
import com.sun.voip.*;


public class Application implements  CallEventListener  {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private String version = "0.0.0.1";
    private Config config;

    private Map< String, Object > callObjects = new HashMap< String, Object >();
    private Map< String, CallParticipant > callPartipants = new HashMap< String, CallParticipant >();
    private static ArrayList<ConferenceMonitor> conferenceMonitors = new ArrayList<ConferenceMonitor>();
    private static ArrayList<Object> incomingCallListeners = new ArrayList<Object>();
    private static ArrayList<Object> outgoingCallListeners = new ArrayList<Object>();

    public boolean appStart(File pluginDirectory) {

        try{
            String logDir = pluginDirectory.getAbsolutePath() + File.separator + ".." + File.separator + ".." + File.separator + "logs" + File.separator;
            loginfo(String.format("VoiceBridge logs %s", logDir));

            config = Config.getInstance();
            config.initialise();

            String webHome = pluginDirectory.getAbsolutePath()  + File.separator + ".." + File.separator + ".." + File.separator + "resources" + File.separator + "spank" + File.separator + "rayo";

            System.setProperty("com.sun.voip.server.LOGLEVEL", "99");
            System.setProperty("com.sun.voip.server.FIRST_RTP_PORT", "3200");
            System.setProperty("com.sun.voip.server.LAST_RTP_PORT", "3899");
            System.setProperty("com.sun.voip.server.FIRST_VIDEOBRIDGE_RTP_PORT", "3900");
            System.setProperty("com.sun.voip.server.LAST_VIDEOBRIDGE_RTP_PORT", "3999");
            System.setProperty("com.sun.voip.server.Bridge.logDirectory", logDir);
            System.setProperty("com.sun.voip.server.BRIDGE_LOG", "bridge.log");
            System.setProperty("com.sun.voip.server.LOGLEVEL", "99");
            System.setProperty("com.sun.voip.server.PUBLIC_IP_ADDRESS", config.getPublicHost());
            System.setProperty("com.sun.voip.server.PROTOCOL", config.getDefaultProtocol());
            System.setProperty("com.sun.voip.server.SIP_PORT", config.getDefaultSIPPort());
            System.setProperty("com.sun.voip.server.Bridge.recordDirectory", webHome + File.separator + "recordings");
            System.setProperty("com.sun.voip.server.Bridge.soundsDirectory", webHome + File.separator + "sounds");
            System.setProperty("com.sun.voip.server.Bridge.soundPath", "/com/sun/voip/server/sounds:" + webHome + File.separator + "sounds");

            System.setProperty("freetts.voices", "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory");

            Properties properties = new Properties();

            properties.setProperty("javax.sip.STACK_NAME", "JAIN SIP 1.1");
            properties.setProperty("javax.sip.RETRANSMISSION_FILTER", "on");
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "99");
            properties.setProperty("gov.nist.javax.sip.MIN_KEEPALIVE_TIME_SECONDS", "360");
            properties.setProperty("gov.nist.javax.sip.SERVER_LOG", logDir + "sip_server.log");
            properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", logDir + "sip_debug.log");
            properties.setProperty("javax.sip.IP_ADDRESS",config.getPrivateHost());

            Bridge.setPublicHost(config.getPublicHost());
            Bridge.setPrivateHost(config.getPrivateHost());
            Bridge.setBridgeLocation("LCL");

            new SipServer(config, properties);
            FreeTTSClient.initialize();

        } catch (Exception e) {

            e.printStackTrace();
        }
        return true;
    }

    public void appStop()
    {
        loginfo( "VoiceBridge stopping");

        Object service = this;

        synchronized (incomingCallListeners)
        {
            boolean removeIncomingCallHandler = false;

            for (Object theService : incomingCallListeners)
            {
                if (theService == service) {
                    removeIncomingCallHandler = true;
                }
            }

            if (removeIncomingCallHandler)
            {
                monitorIncomingCalls(false);
            }

            monitorOutgoingCalls(false);
        }

        synchronized(conferenceMonitors)
        {
            ArrayList<ConferenceMonitor> monitorsToRemove = new ArrayList<ConferenceMonitor>();

            for (ConferenceMonitor m : conferenceMonitors)
            {
                if (service == m.getService())
                {
                    monitorsToRemove.add(m);
                }
            }

            for (ConferenceMonitor m : monitorsToRemove)
            {
                loginfo("Removing conference monitor for " + m.getConferenceId());
                conferenceMonitors.remove(m);
            }
        }

        CallHandler.shutdown();
        config.terminate();
    }


    public void callEventNotification(CallEvent callEvent)
    {
        loginfo( "VoiceBridge callEventNotification " + callEvent.toString());

        if (conferenceMonitors.size() > 0)
        {
            notifyConferenceMonitors(callEvent);
        }

        Application.reportCallEventNotification(null, callEvent, "monitorCallStatus");
    }


    public static void reportCallEventNotification(Object service, CallEvent callEvent, String monitorName)
    {
        if ( service != null )
        {
            String myEvent = CallEvent.getEventString(callEvent.getEvent());
            String callState = callEvent.getCallState().toString();

            String info = callEvent.getInfo() == null ? "" : callEvent.getInfo();
            String dtmf = callEvent.getDtmfKey() == null ? "" : callEvent.getDtmfKey();
            String treatmentdId = callEvent.getTreatmentId() == null ? "" : callEvent.getTreatmentId();
            int noOfCalls = callEvent.getNumberOfCalls();
            String callId = callEvent.getCallId() == null ? "" : callEvent.getCallId();
            String confId = callEvent.getConferenceId() == null ? "" : callEvent.getConferenceId();
            String callInfo = callEvent.getCallInfo() == null ? "" : callEvent.getCallInfo();

            //service.invoke("callsEventNotification", new Object[] {monitorName, myEvent, callState, info, dtmf, treatmentdId, String.valueOf(noOfCalls), callId, confId, callInfo });
        }
    }

    public static void registerNotification(String status, ProxyCredentials credentials)
    {
        log.info("registerNotification " + status + " " + credentials.getXmppUserName());

        try {
            Config.updateStatus(credentials.getXmppUserName(), status);

        } catch (Exception e) {

            System.out.println("registerNotification " + e);
        }
    }

    public void manageCallParticipant(String uid, String parameter, String value)
    {
        loginfo("VoiceBridge manageParticipant");

        if ( callPartipants.containsKey(uid) == false)
        {
            callPartipants.put(uid, new CallParticipant());
            reportInfo("Call Participant " + uid + " created");
        }

        CallParticipant cp = callPartipants.get(uid);

        try {
            parseCallParameters(parameter, value, cp, uid);
            reportInfo("manageCallParticipant processing " + parameter + " value: " + value);

        } catch (Exception e) {

            reportError(e.toString());
        }
    }

    public void manageVoiceBridge(String parameter, String value)
    {
        loginfo("VoiceBridge manageConference");

        try {

            parseBridgeParameters(parameter, value);
            reportInfo("manageVoiceBridge processing " + parameter + " value: " + value);

        } catch (Exception e) {

            reportError(e.toString());
        }
    }

    private void makeOutgoingCall(CallParticipant cp, String uid)
    {
        loginfo("VoiceBridge makeOutgoingCall");

        try {

            if (validateAndAdjustParameters(cp))
            {
                if (cp.getSecondPartyNumber() == null)
                {
                    OutgoingCallHandler outgoingCallHandler = new OutgoingCallHandler(this, cp);
                    outgoingCallHandler.start();

                    callObjects.put(uid, outgoingCallHandler);

                    reportInfo("Outgoing call made to " + cp.getPhoneNumber() + " id: " + cp.getCallId());

                } else {

                    TwoPartyCallHandler twoPartyCallHandler = new TwoPartyCallHandler(this, cp);
                    twoPartyCallHandler.start();

                    callObjects.put(uid, twoPartyCallHandler);

                    reportInfo("Two party call made to " + cp.getPhoneNumber() + " id: " + cp.getCallId());
                }
            }

        } catch (Exception e) {

            reportError(e.toString());
        }
    }

    private void migrateCall(CallParticipant cp)
    {
        loginfo("VoiceBridge migrateCall");

        try {

            if (validateAndAdjustParameters(cp))
            {
                if (cp.migrateCall())
                {
                    new CallMigrator(this, cp).start();

                    reportInfo("Call migrated to " + cp.getSecondPartyNumber() + " id: " + cp.getCallId());

                } else {

                    reportError("Call participant is not configured for migration");
                }
            }

        } catch (Exception e) {

            reportError(e.toString());
        }
    }

    private void reportError(String error)
    {
        logerror("VoiceBridge " + error);
    }

    private void reportInfo(String info)
    {
        loginfo(info);
    }

    private void loginfo( String s ) {

        log.info( s );
        System.out.println( s );
    }

    private void logerror( String s ) {

        log.error( s );
        System.out.println( "[ERROR] " + s );
    }

    private void parseBridgeParameters(String parameter, String value)
    {
        if ("nAvg".equalsIgnoreCase(parameter))
        {
            try {
                LowPassFilter.setNAvg(getInteger(value));
            } catch (Exception e) {
                reportError("parseBridgeParameters : nAvg " + value + " is not numeric" );
            }
            return;
        }

        if ("lpfv".equalsIgnoreCase(parameter))
        {
            try {
                LowPassFilter.setLpfVolumeAdjustment(getDouble(value));
            } catch (Exception e) {
                reportError("parseBridgeParameters : lpfv " + value + " is not numeric" );
            }
            return;
        }

        if ("forceGatewayError".equalsIgnoreCase(parameter))
        {
            try {
                SipTPCCallAgent.forceGatewayError(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("addCallToWhisperGroup".equalsIgnoreCase(parameter))
        {
            String[] tokens = value.split(":");

            if (tokens.length != 2)
            {
                reportError("You must specify both a whisperGroupId and a callId");
                return;
            }

            String whisperGroupId = tokens[0];
            String callId = tokens[1];

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null)
            {
                reportError("Invalid callId:  " + callId);
                return;
            }

            try {
                callHandler.getMember().addCall(whisperGroupId);
            } catch (ParseException e) {
                reportError(e.toString());
            }

            return;
        }

        if ("allowShortNames".equalsIgnoreCase(parameter))
        {
            try {
                ConferenceManager.setAllowShortNames(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("bridgeLocation".equalsIgnoreCase(parameter))
        {
            Bridge.setBridgeLocation(value);
            return;
        }

        if ("cancelCall".equalsIgnoreCase(parameter))
        {
            CallHandler.hangup(value, "User requested call termination");
            return;
        }

        if ("cancelMigration".equalsIgnoreCase(parameter))
        {
            CallMigrator.hangup(value, "User requested call termination");
            return;
        }

        if ("cnThresh".equalsIgnoreCase(parameter))
        {
            try {
                CallHandler.setCnThresh(getQualifierString(value), getInteger(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("comfortNoiseType".equalsIgnoreCase(parameter))
        {
            try {
                MemberSender.setComfortNoiseType(getInteger(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("comfortNoiseLevel".equalsIgnoreCase(parameter))
        {
            try {
                RtpPacket.setDefaultComfortNoiseLevel((byte) getInteger(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("commonMixDefault".equalsIgnoreCase(parameter))
        {
            try {
                WhisperGroup.setCommonMixDefault(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("conferenceInfoShort".equalsIgnoreCase(parameter))
        {
            reportInfo(ConferenceManager.getAbbreviatedConferenceInfo());
            return;
        }

        if ("conferenceInfo".equalsIgnoreCase(parameter))
        {
            reportInfo(ConferenceManager.getDetailedConferenceInfo());
            return;
        }

        if ("createConference".equalsIgnoreCase(parameter))
        {
            String[] tokens = value.split(":");

            if (tokens.length < 2)
            {
                reportError("Missing parameters");
                return;
            }

            String conferenceId = tokens[0];

            if (tokens[1].indexOf("PCM") != 0 && tokens[1].indexOf("SPEEX") != 0)
            {
                reportError("invalid media specification");
                return;
            }

            String mediaPreference = tokens[1];

            String displayName = null;

            if (tokens.length > 2)
            {
                displayName = tokens[2];
            }

            try {
                ConferenceManager.createConference(conferenceId, mediaPreference, displayName);
            } catch (ParseException e) {
                reportError(e.toString());
            }

            return;
        }

        if ("createWhisperGroup".equalsIgnoreCase(parameter))
        {
            String[] tokens = value.split(":");

            if (tokens.length < 2)
            {
                reportError("You must specify both a conferenceId and a whisperGroupId");
                return;
            }

            try {
                String conferenceId = tokens[0];
                String whisperGroupId = tokens[1];

                double attenuation = WhisperGroup.getDefaultAttenuation();

                if (tokens.length == 3)
                {
                    attenuation = getDouble(tokens[2]);
                }

                ConferenceManager.createWhisperGroup(conferenceId,	whisperGroupId, attenuation);

            } catch (ParseException e) {

                reportError("Can't create Whisper group " + tokens[1] + " " + e.getMessage());
            }
            return;
        }

        if ("deferMixing".equalsIgnoreCase(parameter))
        {
            try {
                MemberReceiver.deferMixing(getBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("destroyWhisperGroup".equalsIgnoreCase(parameter))
        {
            String[] tokens = value.split(":");

            if (tokens.length != 2)
            {
                reportError("You must specify both a conferenceId and a whisperGroupId");
                return;
            }

            try {
                ConferenceManager.destroyWhisperGroup(tokens[0], tokens[1]);
            } catch (ParseException e) {
                reportError(e.toString());
            }

            return;
        }

        if ("callAnswerTimeout".equalsIgnoreCase(parameter))
        {
            try {
                CallSetupAgent.setDefaultCallAnswerTimeout(getInteger(value));
            } catch (Exception e) {
                reportError("callAnswerTimeout " + value + " is not numeric" );
            }
            return;
        }

        if ("doNotRecord".equalsIgnoreCase(parameter))
        {
            try {
                boolean booleanValue = getBoolean(value);
                String callId = getQualifierString(value);

                if (callId == null)
                {
                    reportError("Call id is missing");
                    return;
                }

                try {

                    CallHandler.setDoNotRecord(callId, booleanValue);

                } catch (NoSuchElementException e) {
                    reportError("Invalid callId specified:  " + value);
                }

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("dtmfSuppression".equalsIgnoreCase(parameter))
        {
            try {
                boolean booleanValue = getBoolean(value);
                String callId = getQualifierString(value);

                if (callId == null)
                {
                    reportError("Call id is missing");
                    return;
                }

                try {

                    CallHandler.setDtmfSuppression(callId, booleanValue);

                } catch (NoSuchElementException e) {
                    reportError("Invalid callId specified:  " + value);
                }

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("drop".equalsIgnoreCase(parameter))
        {
            try {
                int integerValue = getInteger(value);
                String callId = getQualifierString(value);

                if (callId == null)
                {
                    reportError("Call id is missing");
                    return;
                }

                try {

                    CallHandler.setDropPackets(callId, integerValue);

                } catch (NoSuchElementException e) {
                    reportError("Invalid callId specified:  " + value);
                }

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("duplicateCallLimit".equalsIgnoreCase(parameter))
        {
            try {
                CallHandler.setDuplicateCallLimit(getInteger(value));
            } catch (Exception e) {
                reportError("duplicateCallLimit " + value + " is not numeric" );
            }
            return;
        }


        if ("directConferencing".equalsIgnoreCase(parameter))
        {
            try {
                IncomingCallHandler.setDirectConferencing(getBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("distributedConferenceInfo".equalsIgnoreCase(parameter))
        {
            reportInfo(ConferenceManager.getDistributedConferenceInfo());
            return;
        }

        if ("dropDb".equalsIgnoreCase(parameter))
        {
            ConferenceManager.dropDb();
            return;
        }

        if ("enablePSTNCalls".equalsIgnoreCase(parameter))
        {
            try {
                CallHandler.enablePSTNCalls(getBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("endConference".equalsIgnoreCase(parameter))
        {
            try {
                ConferenceManager.endConference(value);

            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("firstRtpPort".equalsIgnoreCase(parameter))
        {
            try {
                ConferenceMember.setFirstRtpPort(getInteger(value));
            } catch (Exception e) {
                reportError("firstRtpPort " + value + " is not numeric" );
            }
            return;
        }

        if ("setForcePrivateMix".equalsIgnoreCase(parameter))
        {
            try {
                MixManager.setForcePrivateMix(getBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("forwardData".equalsIgnoreCase(parameter))
        {
            try {
                String[] tokens = value.split(":");

                if (tokens.length < 2)
                {
                    reportError("Missing parameters:  " + value);
                    return;
                }

                CallHandler dest = CallHandler.findCall(tokens[0]);

                if (dest == null)
                {
                    reportError("Invalid callId:  " + tokens[0]);
                    return;
                }

                CallHandler src = CallHandler.findCall(tokens[1]);

                if (src == null)
                {
                    reportError("Invalid callId:  " + tokens[1]);
                    return;
                }

                src.getMember().getMemberReceiver().addForwardMember(dest.getMember().getMemberSender());

            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }


        if ("forwardDtmfKeys".equalsIgnoreCase(parameter))
        {
            try {
                MemberReceiver.setForwardDtmfKeys(getBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("gc".equalsIgnoreCase(parameter))
        {
            System.gc();
            return;
        }

        if ("gcs".equalsIgnoreCase(parameter))
        {
            reportInfo(CallHandler.getCallStateForAllCalls());
            return;
        }

        if ("getCallState".equalsIgnoreCase(parameter))
        {
            String callId = getString(value);

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null)
            {
                reportError("Invalid callId:  " + callId);
                return;
            }

            reportInfo(callHandler.getCallState());

            return;
        }

        if ("getAllAbbreviatedMixDescriptors".equalsIgnoreCase(parameter))
        {
            reportInfo(CallHandler.getAllAbbreviatedMixDescriptors());
            return;
        }

        if ("getAbbreviatedMixDescriptors".equalsIgnoreCase(parameter))
        {
            String callId = getString(value);

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null)
            {
                reportError("Invalid callId:  " + callId);
                return;
            }

            reportInfo(callHandler.getMember().getAbbreviatedMixDescriptors());
            return;
        }

        if ("getAllMixDescriptors".equalsIgnoreCase(parameter))
        {
            reportInfo(CallHandler.getAllMixDescriptors());
            return;
        }

        if ("getMixDescriptors".equalsIgnoreCase(parameter))
        {
            String callId = getString(value);

            CallHandler callHandler = CallHandler.findCall(callId);

            if (callHandler == null)
            {
                reportError("Invalid callId:  " + callId);
                return;
            }

            reportInfo(callHandler.getMember().getMixDescriptors());
            return;
        }

        if ("getStatistics".equalsIgnoreCase(parameter))
        {
            /*
            { String.valueOf(ConferenceManager.getNumberOfConferences()),
              String.valueOf(ConferenceManager.getTotalMembers()),
              String.valueOf(CallHandler.getTotalSpeaking()),
              String.valueOf(Math.round(ConferenceSender.getTimeBetweenSends() * 10000) / 10000.),
              String.valueOf(Math.round(ConferenceSender.getAverageSendTime() * 10000) / 10000.),
              String.valueOf(Math.round(ConferenceSender.getMaxSendTime() * 10000) / 10000.)

            });
            */
        }

        if ("getBriefConferenceInfo".equalsIgnoreCase(parameter))
        {
            reportInfo(ConferenceManager.getBriefConferenceInfo());
            return;
        }

        if ("gc".equalsIgnoreCase(parameter))
        {
            System.gc();
            return;
        }

        if ("incomingCallTreatment".equalsIgnoreCase(parameter))
        {
            IncomingCallHandler.setIncomingCallTreatment(value);
            return;
        }

        if ("incomingCallVoiceDetection".equalsIgnoreCase(parameter))
        {
            try {
                IncomingCallHandler.setIncomingCallVoiceDetection(getBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("internationalPrefix".equalsIgnoreCase(parameter))
        {
            if (value.equals("\"\"") || value.equals("''"))
            {
                value = "";
            }

            config.setInternationalPrefix(value);
            return;
        }

        if ("internalExtenLength".equalsIgnoreCase(parameter))
        {
            try {
                config.setInternalExtenLength(getInteger(value));
            } catch (Exception e) {
                reportError("internalExtenLength " + value + " is not numeric" );
            }
        }

        if ("conferenceJoinTreatment".equalsIgnoreCase(parameter))
        {
            try {
                String[] tokens = value.split(":");

                if (tokens.length != 2)
                {
                   reportError("conferenceJoinTreatment requires two inputs");
                   return;
                }

                ConferenceManager.setConferenceJoinTreatment(tokens[1], tokens[0]);

            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("conferenceLeaveTreatment".equalsIgnoreCase(parameter))
        {
            try {
                String[] tokens = value.split(":");

                if (tokens.length != 2)
                {
                   reportError("conferenceLeaveTreatment requires two inputs");
                   return;
                }

                ConferenceManager.setConferenceLeaveTreatment(tokens[1], tokens[0]);

            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }


        if ("lastRtpPort".equalsIgnoreCase(parameter))
        {
            try {
                ConferenceMember.setLastRtpPort(getInteger(value));
            } catch (Exception e) {
                reportError("lastRtpPort " + value + " is not numeric" );
            }
            return;
        }


        if ("loneReceiverPort".equalsIgnoreCase(parameter))
        {
            try {
                ConferenceManager.setLoneReceiverPort(getInteger(value));
            } catch (Exception e) {
                reportError("loneReceiverPort " + value + " is not numeric" );
            }
            return;
        }

        if ("longDistancePrefix".equalsIgnoreCase(parameter))
        {
            if (value.equals("\"\"") || value.equals("''"))
            {
                value = "";
            }

            config.setLongDistancePrefix(value);
            return;
        }

        if ("migrateToBridge".equalsIgnoreCase(parameter))
        {
            try {
                String[] tokens = value.split(":");

                if (tokens.length != 3)
                {
                   reportError("migrateToBridge requires three inputs");
                   return;
                }

                String bridge = tokens[0];
                String port = tokens[1];
                String callId = tokens[2];

                CallHandler callHandler = CallHandler.findCall(callId);

                if (callHandler == null)
                {
                    reportError("Invalid callId: " + callId);
                    return;
                }

                CallParticipant cp = callHandler.getCallParticipant();

                if (cp.getInputTreatment() != null)
                {
                    cp.setPhoneNumber(null);
                }
/*
                BridgeConnector bridgeConnector;

                int serverPort;

                try {
                    serverPort = Integer.parseInt(port);

                } catch (NumberFormatException e) {

                    reportError("Invalid bridge server port:  " + port);
                    return;
                }

                try {
                    bridgeConnector = new BridgeConnector(bridge, serverPort, 5000);

                } catch (IOException e) {
                    reportError("Unable to connect to bridge " + bridge	+ " " + e.getMessage());
                    return;
                }

                callHandler.suppressStatus(true);

                try {
                    String s = cp.getCallSetupRequest();
                    s = s.substring(0, s.length() - 1);  // get rid of last new line
                    bridgeConnector.sendCommand(s);

                } catch (IOException e) {

                    reportError("Unable to send command to bridge:  " + e.getMessage());
                    return;
                }

                bridgeConnector.addCallEventListener(requestHandler);

                // XXX need to figure out how to deal with Private Mixes now that the call has moved!
*/
            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("senderThreads".equalsIgnoreCase(parameter))
        {
            try {
                ConferenceSender.setSenderThreads(getInteger(value));
            } catch (Exception e) {
                reportError("senderThreads " + value + " is not numeric" );
            }
            return;
        }

        if ("minJitterBufferSize".equalsIgnoreCase(parameter))
        {
            try {
                String[] tokens = value.split(":");

                if (tokens.length != 2)
                {
                   reportError("minJitterBufferSize requires two inputs");
                   return;
                }

                int minJitterBufferSize = getInteger(tokens[0]);
                CallHandler callHandler = CallHandler.findCall(tokens[1]);

                if (callHandler == null)
                {
                    reportError("Invalid callId:  " + tokens[1]);
                    return;
                }

                callHandler.getMember().getMemberReceiver().setMinJitterBufferSize(minJitterBufferSize);

            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("maxJitterBufferSize".equalsIgnoreCase(parameter))
        {
            try {
                String[] tokens = value.split(":");

                if (tokens.length != 2)
                {
                   reportError("maxJitterBufferSize requires two inputs");
                   return;
                }

                int minJitterBufferSize = getInteger(tokens[0]);
                CallHandler callHandler = CallHandler.findCall(tokens[1]);

                if (callHandler == null)
                {
                    reportError("Invalid callId:  " + tokens[1]);
                    return;
                }

                callHandler.getMember().getMemberReceiver().setMaxJitterBufferSize(minJitterBufferSize);

            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("monitorCallStatus".equalsIgnoreCase(parameter))
        {
            try {
                boolean booleanValue = getBoolean(value);
                String callId = getQualifierString(value);

                if (callId == null)
                {
                    reportError("Call id is missing");
                    return;
                }

                try {
                    CallHandler callHandler = CallHandler.findCall(callId);

                    if (callHandler == null)
                    {
                        reportError("No such callId:  " + callId);
                        return;
                    }

                    if (booleanValue == true)
                    {
                        callHandler.addCallEventListener(this);

                    } else {

                        callHandler.removeCallEventListener(this);
                    }

                } catch (NoSuchElementException e) {
                    reportError("Invalid callId specified:  " + value);
                }

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("monitorConferenceStatus".equalsIgnoreCase(parameter))
        {
            try {
                boolean booleanValue = getBoolean(value);
                String conferenceId = getQualifierString(value);

                if (conferenceId == null)
                {
                    reportError("conferenceId id is missing");
                    return;
                }

                monitorConferenceStatus(conferenceId, booleanValue);

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("monitorIncomingCalls".equalsIgnoreCase(parameter))
        {
            try {
                boolean booleanValue = getBoolean(value);

                if (monitorIncomingCalls(booleanValue) == false)
                {
                    reportInfo("There is already an incoming call monitor!");
                    return;
                }

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("monitorOutgoingCalls".equalsIgnoreCase(parameter))
        {
            try {
                boolean booleanValue = getBoolean(value);
                monitorOutgoingCalls(booleanValue);

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("muteCall".equalsIgnoreCase(parameter))
        {
            try {
                boolean booleanValue = getBoolean(value);
                String callId = getQualifierString(value);

                if (callId == null)
                {
                    reportError("Call id is missing");
                    return;
                }

                try {
                    CallHandler callHandler = CallHandler.findCall(callId);

                    if (callHandler == null)
                    {
                        reportError("No such callId:  " + callId);
                        return;
                    }

                    CallHandler.setMuted(callId, booleanValue);

                } catch (NoSuchElementException e) {
                    reportError("Invalid callId specified:  " + value);
                }

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("muteWhisperGroup".equalsIgnoreCase(parameter))
        {
            try {
                boolean booleanValue = getBoolean(value);
                String callId = getQualifierString(value);

                if (callId == null)
                {
                    reportError("Call id is missing");
                    return;
                }

                try {
                    CallHandler callHandler = CallHandler.findCall(callId);

                    if (callHandler == null)
                    {
                        reportError("No such callId:  " + callId);
                        return;
                    }

                    CallHandler.setMuteWhisperGroup(callId, booleanValue);

                } catch (NoSuchElementException e) {
                    reportError("Invalid callId specified:  " + value);
                }

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("muteConference".equalsIgnoreCase(parameter))
        {
            try {
                boolean booleanValue = getBoolean(value);
                String callId = getQualifierString(value);

                if (callId == null)
                {
                    reportError("Call id is missing");
                    return;
                }

                try {
                    CallHandler callHandler = CallHandler.findCall(callId);

                    if (callHandler == null)
                    {
                        reportError("No such callId:  " + callId);
                        return;
                    }

                    CallHandler.setConferenceMuted(callId, booleanValue);

                } catch (NoSuchElementException e) {
                    reportError("Invalid callId specified:  " + value);
                }

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("numberOfCalls".equalsIgnoreCase(parameter))
        {
            try {
                String[] tokens = value.split(":");

                if (tokens.length != 1)
                {
                    reportError("You must specify a conference id");
                    return;
                }

                CallEvent event = new CallEvent(CallEvent.NUMBER_OF_CALLS);
                event.setNumberOfCalls(ConferenceManager.getNumberOfMembers(tokens[0]));
                //callEventNotification(event);

            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("outsideLinePrefix".equalsIgnoreCase(parameter))
        {
            if (value.equals("\"\"") || value.equals("''"))
            {
                value = "";
            }

            config.setOutsideLinePrefix(value);
            return;
        }

        if ("receiverPause".equalsIgnoreCase(parameter))
        {
            try {
                ConferenceReceiver.setReceiverPause(getInteger(value));
            } catch (Exception e) {
                reportError("pause " + value + " is not numeric" );
            }
            return;
        }

        if ("pauseTreatmentToCall".equalsIgnoreCase(parameter))
        {
            try {
                String[] tokens = value.split(":");

                CallHandler callHandler = CallHandler.findCall(tokens[0]);

                if (callHandler == null)
                {
                    reportError("Invalid callId:  " + tokens[0]);
                    return;
                }

                String treatmentId = null;

                if (tokens.length > 1)
                {
                    treatmentId = tokens[1];
                }

                callHandler.getMember().pauseTreatment(treatmentId, true);

            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("pauseTreatmentToConference".equalsIgnoreCase(parameter))
        {
            try {
                String treatment = getTreatment(value);
                value = value.substring(treatment.length());
                String conferenceId = getQualifierString(value);

                if (conferenceId == null)
                {
                    reportError("conferenceId must be specified:  " + value);
                    return;
                }

                ConferenceManager.pauseTreatment(conferenceId, treatment, true);

            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("playTreatmentToCall".equalsIgnoreCase(parameter))
        {
            try {
                String treatment = getTreatment(value);
                double volume[] = getVolume(value);

                value = value.substring(treatment.length());
                String callId = getQualifierString(value);

                if (callId == null)
                {
                    reportError("callId must be specified:  " + value);
                    return;
                }

                try {
                    CallHandler.playTreatmentToCall(callId, treatment);

                } catch (NoSuchElementException e) {
                    reportError("Invalid callId specified:  " + value);
                    return;

                } catch (IOException e) {
                    reportError("Unable to read treatment file " + treatment + " " + e.getMessage());
                    return;
                }

            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("playTreatmentToConference".equalsIgnoreCase(parameter))
        {
            try {
                String treatment = getTreatment(value);
                double volume[] = getVolume(value);

                value = value.substring(treatment.length());
                String conferenceId = getQualifierString(value);

                if (conferenceId == null)
                {
                    reportError("conference Id must be specified:  " + value);
                    return;
                }

                try {
                     ConferenceManager.playTreatment(conferenceId, treatment);

                } catch (NoSuchElementException e) {
                    reportError("Invalid conference Id specified:  " + value);
                    return;
                }

            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("playTreatmentToAllConferences".equalsIgnoreCase(parameter))
        {
            try {
                String treatment = getTreatment(value);
                double volume[] = getVolume(value);
                value = value.substring(treatment.length());

                try {
                     ConferenceManager.playTreatmentToAllConferences(treatment);

                } catch (Exception e) {
                    reportError("Error playing treatment :  " + value);
                    return;
                }

            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("transferCall".equalsIgnoreCase(parameter))
        {
            try {
                String callId = getString(value);

                if (callId == null)
                {
                    reportError("Call id is missing");
                    return;
                }

                try {
                    CallHandler callHandler = CallHandler.findCall(callId);

                    if (callHandler == null)
                    {
                        reportError("No such callId:  " + callId);
                        return;
                    }

                    String conferenceId = getQualifierString(value);
                    IncomingCallHandler.transferCall(callId, conferenceId);

                } catch (NoSuchElementException e) {
                    reportError("Invalid callId specified:  " + value);
                }

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("sendDtmfKey".equalsIgnoreCase(parameter))
        {
            try {
                String callId = getString(value);

                if (callId == null)
                {
                    reportError("Call id is missing");
                    return;
                }

                try {
                    CallHandler callHandler = CallHandler.findCall(callId);

                    if (callHandler == null)
                    {
                        reportError("No such callId:  " + callId);
                        return;
                    }

                    String dtmfKey = getQualifierString(value);
                    callHandler.dtmfKeys(dtmfKey);

                } catch (NoSuchElementException e) {
                    reportError("Invalid callId specified:  " + value);
                }

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }
    }


    private void parseCallParameters(String parameter, String value, CallParticipant cp, String uid)
    {
        if ("makeCall".equalsIgnoreCase(parameter))
        {
            try {
                makeOutgoingCall(cp, uid);
            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("migrateCall".equalsIgnoreCase(parameter))
        {
            try {
                migrateCall(cp);
            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("cancelCall".equalsIgnoreCase(parameter))
        {
            CallHandler.hangup(cp.getCallId(), "User requested call termination");
            return;
        }

        if ("sendDtmfKey".equalsIgnoreCase(parameter))
        {
            try {
                CallHandler callHandler = CallHandler.findCall(cp.getCallId());
                callHandler.dtmfKeys(value);

            } catch (NoSuchElementException e) {
                reportError("Invalid callId specified:  " + value);
            }

            return;
        }

        if ("conferenceJoinTreatment".equalsIgnoreCase(parameter))
        {
            cp.setConferenceJoinTreatment(value);
            return;
        }

        if ("conferenceLeaveTreatment".equalsIgnoreCase(parameter))
        {
            cp.setConferenceLeaveTreatment(value);
            return;
        }

        if ("callAnsweredTreatment".equalsIgnoreCase(parameter))
        {
             cp.setCallAnsweredTreatment(value);
            return;
        }

        if ("callanswertimeout".equalsIgnoreCase(parameter))
        {
            try {
                cp.setCallAnswerTimeout(getInteger(value));
            } catch (Exception e) {
                reportError("callAnswerTimeout " + value + " is not numeric" );
            }
            return;
        }

        if ("calltimeout".equalsIgnoreCase(parameter))
        {
            try {
                cp.setCallTimeout(getInteger(value) * 1000);
            } catch (Exception e) {
                reportError("callTimeout " + value + " is not numeric" );
            }
            return;
        }

        if ("callendtreatment".equalsIgnoreCase(parameter))
        {
            cp.setCallEndTreatment(value);
            return;
        }

        if ("callestablishedtreatment".equalsIgnoreCase(parameter))
        {
            cp.setCallEstablishedTreatment(value);
            return;
        }

        if ("callid".equalsIgnoreCase(parameter))
        {
            cp.setCallId(value);
            return;
        }

        if ("conferenceid".equalsIgnoreCase(parameter))
        {
            try {
                String[] tokens = value.split(":");

                cp.setConferenceId(tokens[0].trim());

                if (tokens.length > 1) {
                    cp.setMediaPreference(tokens[1]);
                }

                if (tokens.length > 2) {
                    cp.setConferenceDisplayName(tokens[2]);
                }

            } catch (Exception e) {
                reportError("conferenceId " + value + " is invalid" );
            }
            return;
        }

        if ("displayname".equalsIgnoreCase(parameter))
        {
            cp.setDisplayName(value);
            return;
        }

        if ("distributedbridge".equalsIgnoreCase(parameter))
        {
            try {
                cp.setDistributedBridge(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("donotrecord".equalsIgnoreCase(parameter))
        {
            try {
                cp.setDoNotRecord(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("dtmfdetection".equalsIgnoreCase(parameter))
        {
            try {
                cp.setDtmfDetection(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("dtmfsuppression".equalsIgnoreCase(parameter))
        {
            try {
                cp.setDtmfSuppression(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("forwarddatafrom".equalsIgnoreCase(parameter))
        {
            cp.setForwardingCallId(value);
            return;
        }

        if ("ignoretelephoneevents".equalsIgnoreCase(parameter))
        {
            try {
                cp.setIgnoreTelephoneEvents(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("inputtreatment".equalsIgnoreCase(parameter))
        {
            cp.setInputTreatment(value);
            return;
        }

        if ("encryptkey".equalsIgnoreCase(parameter))
        {
            cp.setEncryptionKey(value);
            return;
        }

        if ("encryptalgorithm".equalsIgnoreCase(parameter))
        {
            cp.setEncryptionAlgorithm(value);
            return;
        }

        if ("firstConferenceMemberTreatment".equalsIgnoreCase(parameter))
        {
            cp.setFirstConferenceMemberTreatment(value);
            return;
        }

        if ("handlesessionprogress".equalsIgnoreCase(parameter))
        {
            try {
                cp.setHandleSessionProgress(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("joinconfirmationkey".equalsIgnoreCase(parameter))
        {
            try {
                MemberReceiver.setJoinConfirmationKey(value);

            } catch (Exception e) {
                reportError(e.toString());
            }
            return;
        }

        if ("joinconfirmationtimeout".equalsIgnoreCase(parameter))
        {
            try {
                cp.setJoinConfirmationTimeout(getInteger(value));
            } catch (Exception e) {
                reportError("callAnswerTimeout " + value + " is not numeric" );
            }
            return;
        }

        if ("mediapreference".equalsIgnoreCase(parameter))
        {
            cp.setMediaPreference(value);
            return;
        }

        if ("migrate".equalsIgnoreCase(parameter))
        {
            String callId = getFirstString(value);
            cp.setCallId(callId);

            /*
             * The second party number may be a sip address
             * with colons.  So we treat everything after the
             * first colon as the second party number.
             */

            int ix;

            if ((ix = value.indexOf(":")) < 0) {

                reportError("secondPartyNumber must be specified:  " + value);
                return;
            }

            String secondPartyNumber = value.substring(ix + 1);

            if (secondPartyNumber == null)
            {
                reportError("secondPartyNumber must be specified:  " + value);
                return;
            }

            try {
                cp.setSecondPartyNumber(secondPartyNumber);
                cp.setMigrateCall(true);

            } catch (Exception e) {
                reportError(e.toString());
            }

            return;
        }

        if ("mute".equalsIgnoreCase(parameter))
        {
            try {
                cp.setMuted(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("mutewhispergroup".equalsIgnoreCase(parameter))
        {
            try {
                cp.setMuteWhisperGroup(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("muteconference".equalsIgnoreCase(parameter))
        {
            try {
                cp.setConferenceMuted(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("name".equalsIgnoreCase(parameter))
        {
            cp.setName(value);
            return;
        }

        if ("phonenumber".equalsIgnoreCase(parameter))
        {
            cp.setPhoneNumber(value);
            return;
        }

        if ("phonenumberlocation".equalsIgnoreCase(parameter))
        {
            cp.setPhoneNumberLocation(value);
        }

        if ("protocol".equalsIgnoreCase(parameter))
        {
            if (value.equalsIgnoreCase("SIP") == false && value.equalsIgnoreCase("NS") == false && value.equalsIgnoreCase("WebRtc") == false && value.equalsIgnoreCase("Rtmfp") == false && value.equalsIgnoreCase("Speaker") == false)
            {
                reportError("Invalid protocol:  " + value);
                return;
            }

            cp.setProtocol(value);
            return;
        }

        if ("recorder".equalsIgnoreCase(parameter))
        {
            try {
                cp.setRecorder(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }


        if ("recorddirectory".equalsIgnoreCase(parameter))
        {
            cp.setRecordDirectory(value);
            return;
        }

        if ("remotecallid".equalsIgnoreCase(parameter))
        {
            cp.setRemoteCallId(value);
            return;
        }

        if ("voiceDetectionWhileMuted".equalsIgnoreCase(parameter))
        {
            try {
                cp.setVoiceDetectionWhileMuted(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("useConferenceReceiverThread".equalsIgnoreCase(parameter))
        {
            try {
                cp.setUseConferenceReceiverThread(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("voiceDetection".equalsIgnoreCase(parameter))
        {
            try {
                cp.setVoiceDetection(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("whisperGroup".equalsIgnoreCase(parameter))
        {
            cp.setWhisperGroupId(value);
            return;
        }
        if ("voipGateway".equalsIgnoreCase(parameter))
        {
            cp.setVoIPGateway(value);
            return;
        }

        if ("secondPartyCallId".equalsIgnoreCase(parameter))
        {
            cp.setSecondPartyCallId(value);
            return;
        }
        if ("sipProxy".equalsIgnoreCase(parameter))
        {
            cp.setSipProxy(value);
            return;
        }

        if ("secondPartyCallId".equalsIgnoreCase(parameter))
        {
            cp.setSecondPartyCallId(value);
            return;
        }
        if ("secondPartyCallEndTreatment".equalsIgnoreCase(parameter))
        {
            cp.setSecondPartyCallEndTreatment(value);
            return;
        }

        if ("secondPartyName".equalsIgnoreCase(parameter))
        {
            cp.setSecondPartyName(value);
            return;
        }

        if ("secondPartyNumber".equalsIgnoreCase(parameter))
        {
            cp.setSecondPartyNumber(value);
            return;
        }

        if ("secondPartyTreatment".equalsIgnoreCase(parameter))
        {
            cp.setSecondPartyTreatment(value);
            return;
        }

        if ("secondpartyVoiceDetection".equalsIgnoreCase(parameter))
        {
            try {
                cp.setSecondPartyVoiceDetection(stringToBoolean(value));
            } catch (ParseException e) {
                reportError(e.toString());
            }
            return;
        }

        if ("secondPartyTimeout".equalsIgnoreCase(parameter))
        {
            try {
                cp.setSecondPartyTimeout(getInteger(value));
            } catch (Exception e) {
                reportError("setSecondPartyTimeout " + value + " is not numeric" );
            }
            return;
        }
    }

    private String getString(String value)
    {
        int n;

        if ((n = value.lastIndexOf(":")) > 0) {
            value = value.substring(0, n);
        }

        return value;
    }

    private double[] getVolume(String value) throws ParseException
    {
        String v = new String(value);

        int n;

            if ((n = v.indexOf(":volume=")) < 0) {
            return null;
        }

        v = v.substring(n + 8);

        String[] tokens = v.split(":");

        double[] volume = new double[tokens.length];

        for (int i = 0; i < volume.length; i++)
        {
            try {
                volume[i] = Double.parseDouble(tokens[i]);

            } catch (NumberFormatException e) {

            throw new ParseException("Invalid floating point value: "	+ tokens[i], 0);
            }
        }

        return volume;
    }

    private String getTreatment(String value)
    {
        String v = new String(value);

        int n;

        if ((n = v.indexOf(":volume=")) >= 0)
        {
            v = v.substring(0, n);
        }

        if ((n = v.lastIndexOf(":")) < 0) {
            /*
             * There's no ":", so the whole string is the treatment
             */
            return v;
        }

        String s = v.substring(0, n);

        if (s.equalsIgnoreCase("f") || s.equalsIgnoreCase("file")
               || s.equalsIgnoreCase("d") || s.equalsIgnoreCase("dtmf")
               || s.equalsIgnoreCase("t") || s.equalsIgnoreCase("tts")) {

            /*
             * The only ":" is preceded by the type of treatment.
             * The whole string is the treatment.
             */
            return v;
        }

        /*
         * The treatment is the string up to the last ":".
         */
        return s;
    }

    private boolean getBoolean(String value) throws ParseException
    {
        int n;

        if ((n = value.indexOf(":")) > 0) {
            value = value.substring(0, n);
        }

        return stringToBoolean(value);
    }


    private boolean stringToBoolean(String value) throws ParseException
    {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("t")) {
            return true;
        }

        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("F")) {
            return false;
        }

        throw new ParseException("Invalid boolean value, must be true or false:  " + value, 0);
    }

    private int getInteger(String value) throws ParseException
    {
        int n;

        if ((n = value.indexOf(":")) > 0) {
            value = value.substring(0, n);
        }

        int i = 0;

        try {
                i = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid integer value: " + value, 0);
        }

        return i;
    }

    private String getQualifierString(String value)
    {
        int n;

        if (value == null) {
            return null;
        }

        if ((n = value.lastIndexOf(":")) >= 0) {
            return value.substring(n+1);
        }

        return null;
    }

    private double getDouble(String value) throws ParseException
    {
        int n;

        if ((n = value.indexOf(":")) > 0) {
            value = value.substring(0, n);
        }

        double f = 0.0;

        try {
                f = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid double value: " + value, 0);
        }

        return f;
    }

    private String getFirstString(String value)
    {
        int ix;

        if ((ix = value.indexOf(":")) < 0) {
            return value;
        }

        return value.substring(0, ix);
    }

    private boolean validateAndAdjustParameters(CallParticipant cp) throws ParseException
    {
        String callId = cp.getCallId();

        if (callId == null) {
            cp.setCallId(CallHandler.getNewCallId());

        } else {

            if (callId.equals("0"))
            {
                reportError("Zero is an invalid callId");
                return false;
            }

            if (cp.migrateCall() == false)
            {
                CallHandler callHandler = CallHandler.findCall(callId);

                if (callHandler != null)
                {
                    if (callHandler.isCallEnding() == false)
                    {
                        reportError("CallId " + callId + " is already in use");
                        return false;

                    } else {
                        reportError("Reusing callId for ending call " + callId);
                    }
                }
            }
        }

        handleCallAttendant(cp);

        cp.setSecondPartyNumber(config.formatPhoneNumber(cp.getSecondPartyNumber(), cp.getPhoneNumberLocation()));

        if (cp.migrateCall() == false)
        {
            if (cp.getProtocol() == null || "SIP".equals(cp.getProtocol()))
            {
                cp.setPhoneNumber(config.formatPhoneNumber(cp.getPhoneNumber(), cp.getPhoneNumberLocation()));

                if (cp.getPhoneNumber() == null)
                {
                    if (cp.getInputTreatment() == null)
                    {
                        reportError("You must specify a phone number or a soft phone URI");
                        return false;

                    } else {

                        if (cp.getInputTreatment().equals("null"))
                        {
                            cp.setInputTreatment("");
                        }

                        cp.setPhoneNumber(cp.getInputTreatment());
                        cp.setProtocol("NS");
                    }
                }
            }
        }

        if (cp.getName() == null || cp.getName().equals(""))
        {
            cp.setName("Anonymous");
        }

        if (cp.migrateCall() == true)
        {
            if (cp.getCallId() == null || cp.getSecondPartyNumber() == null)
            {
                reportError("You must specify old and new phone numbers to migrate a call");
                return false;
            }
        }

        if (cp.getConferenceId() == null &&	cp.getSecondPartyNumber() == null)
        {
            reportError("You must specify a conference Id");
            return false;
        }

        if (cp.getDisplayName() == null)
        {
            if (cp.getSecondPartyNumber() == null)
            {
                cp.setDisplayName(cp.getConferenceId());

            } else {

                if (cp.getSecondPartyName() != null)
                {
                    cp.setDisplayName(cp.getSecondPartyName());
                } else {
                    cp.setDisplayName(cp.getSecondPartyNumber());
                }
            }
        }

        /*
         * For two party calls.
         */

        if (cp.getConferenceId() != null)
        {
            if (cp.getSecondPartyTreatment() != null)
            {
                cp.setConferenceJoinTreatment(cp.getSecondPartyTreatment());
            }

            if (cp.getSecondPartyCallEndTreatment() != null)
            {
                cp.setConferenceLeaveTreatment(
                cp.getSecondPartyCallEndTreatment());
            }
        }

        if (cp.getSecondPartyNumber() != null)
        {
            if (cp.getConferenceId() == null)
            {
                cp.setConferenceId(cp.getPhoneNumber());
            }
        }

        return true;
    }


    /*
     * Some Sun sites such as China have an automated call attendent
     * which asks the user to enter the extension again.
     *
     * If a phone number has ">" in it, we replace the phone number
     * to call with everything before the ">" and set the call
     * answer treatment to be dtmf keys of everything after the ">".
     */

    private void handleCallAttendant(CallParticipant cp)
    {
        String phoneNumber = cp.getPhoneNumber();

        int ix;

        if (phoneNumber != null && phoneNumber.indexOf("sip:") < 0 && phoneNumber.indexOf("@") < 0)
        {
            if ((ix = phoneNumber.indexOf(">")) > 0)
            {
                /*
                 * Must have 5 digit extension
                 */
                if (phoneNumber.length() >= ix + 1 + 5) {
                    cp.setCallAnsweredTreatment("dtmf:" +
                        phoneNumber.substring(ix + 1));

                    cp.setPhoneNumber(phoneNumber.substring(0, ix));
                }
            }
        }

        phoneNumber = cp.getSecondPartyNumber();

        if (phoneNumber != null &&
                phoneNumber.indexOf("sip:") < 0 &&
                phoneNumber.indexOf("@") < 0) {

            if ((ix = phoneNumber.indexOf(">")) > 0) {
                /*
                 * Must have 5 digit extension
                 */
                if (phoneNumber.length() >= ix + 1 + 5) {
                    cp.setSecondPartyTreatment("dtmf:" +
                        phoneNumber.substring(ix + 1));

                    cp.setSecondPartyNumber(phoneNumber.substring(0, ix));
                }
            }
        }
    }

    private void monitorConferenceStatus(String conferenceId, boolean monitor)
    {
        if (monitor)
        {
            synchronized(conferenceMonitors)
            {
                loginfo("adding conference monitor for " + conferenceId);
                conferenceMonitors.add(new ConferenceMonitor(null, conferenceId));
            }

        } else {

            synchronized(conferenceMonitors)
            {
                ArrayList<ConferenceMonitor> monitorsToRemove = new ArrayList<ConferenceMonitor>();

                for (ConferenceMonitor m : conferenceMonitors)
                {
                    if (null != m.getService())
                    {
                        continue;
                    }

                    if (conferenceId.equals(m.getConferenceId()))
                    {
                        monitorsToRemove.add(m);
                    }
                }

                for (ConferenceMonitor m : monitorsToRemove)
                {
                    loginfo("Removing conference monitor for " + conferenceId);
                    conferenceMonitors.remove(m);
                }
            }
        }
    }

    public void monitorOutgoingCalls(boolean monitor)
    {
        synchronized(outgoingCallListeners)
        {
            if (monitor == true) {
                outgoingCallListeners.add(this);
            } else {
                outgoingCallListeners.remove(this);
            }
        }
    }

    public boolean monitorIncomingCalls(boolean monitor)
    {
        synchronized(incomingCallListeners)
        {
            if (monitor == true)
            {
                if (incomingCallListeners.contains(this))
                {
                    reportError("Client already has an incomingCallListener");
                    return false;
                }

                incomingCallListeners.add(this);
                IncomingCallHandler.setDirectConferencing(false);

                loginfo("adding incoming call monitor, setting directConferencing to false");

            } else {

                if (incomingCallListeners.contains(this) == false)
                {
                    return false;
                }

                incomingCallListeners.remove(this);

                if (incomingCallListeners.size() == 0)
                {
                    IncomingCallHandler.setDirectConferencing(true);
                    loginfo("removing last incoming call monitor setting directConferencing to true");
                }
            }
        }
        return true;
    }

    public static void notifyConferenceMonitors(CallEvent callEvent)
    {
        //System.out.println("notifyConferenceMonitors " + callEvent.toString());

        synchronized(conferenceMonitors)
        {
            /*
             * Notify conference listeners
             */

            String conferenceId = callEvent.getConferenceId();

            String s = callEvent.toString();

            if (conferenceId == null)
            {
                int ix;

                String search = "ConferenceId='";

                if ((ix = s.indexOf(search)) < 0) {
                    return;
                }

                conferenceId = s.substring(search.length());

                int end;

                if ((end = conferenceId.indexOf("'")) < 0) {
                    return;
                }

                conferenceId = conferenceId.substring(0, end);
            }

            for (ConferenceMonitor m : conferenceMonitors)
            {
                    if (conferenceId.equals(m.getConferenceId()))
                    {
                        Application.reportCallEventNotification(m.getService(), callEvent, "monitorConferenceStatus");
                    }
            }
        }
    }

    public static void incomingCallNotification(CallEvent callEvent)
    {
        //System.out.println("incomingCallNotification " + callEvent.toString());

        synchronized (incomingCallListeners)
        {
            for (Object service : incomingCallListeners)
            {
                Application.reportCallEventNotification(service, callEvent, "monitorIncomingCalls");
            }
        }

        notifyConferenceMonitors(callEvent);
    }

    public static void outgoingCallNotification(CallEvent callEvent)
    {
        //System.out.println("outgoingCallNotification " + callEvent.toString());

        synchronized(outgoingCallListeners)
        {
            for (Object service : outgoingCallListeners)
            {
                Application.reportCallEventNotification(service, callEvent, "monitorOutgoingCalls");

            }
        }
    }


    class ConferenceMonitor {

        private Object service;
        private String conferenceId;

        public ConferenceMonitor(Object service, String conferenceId) {
            this.service = service;
            this.conferenceId = conferenceId;
        }

        public Object getService() {
            return service;
        }

        public String getConferenceId() {
            return conferenceId;
        }

    }

}
