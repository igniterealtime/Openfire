/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation and distributed hereunder
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this
 * code.
 */

package com.sun.voip.server;

import com.sun.voip.CallParticipant;
import com.sun.voip.CallEvent;
import com.sun.voip.CallEventListener;
import com.sun.voip.CallState;
import com.sun.voip.Logger;
import com.sun.voip.TreatmentDoneListener;
import com.sun.voip.TreatmentManager;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.SocketException;

import java.util.NoSuchElementException;
import java.util.Vector;

import java.text.ParseException;

/**
 * Common code for handling incoming outgoing calls.
 */
public abstract class CallHandler extends Thread {
    protected ConferenceManager conferenceManager;
    protected ConferenceMember member;
    protected MemberSender memberSender;
    protected MemberReceiver memberReceiver;
    protected CallSetupAgent csa;
    protected CallParticipant cp;

    protected boolean done = false;
    protected String reasonCallEnded;

    protected boolean suppressStatus;

    /*
     * maintain a list of active calls so individual calls
     * can be terminated.
     */
    protected static Vector activeCalls = new Vector();

    private Vector callEventListeners = new Vector();

    /*
     * One receiver per conference is ideal for scaling.
     * In order for this to work, we must be able to distinguish
     * calls based on the source address in packets we receive
     * from each call.  The SIP port exchange only involves destination
     * ports.  For most implmentations (Cisco Gateway, Cisco IP Phone,
     * conference bridge software) the source and destination ports
     * are the same.  Sip-Communicator is one exception.
     *
     * So when we see a sip URI for a target phone number, we create
     * a separate conference receiver specifically for that call.
     */
    private static boolean oneReceiverPerConference = true;

    private static int duplicateCallLimit = 100;

    private static boolean enablePSTNCalls = true;

    /*
     * Used by the OutgoingCallHandler to handle two party calls.
     * This is here so that dtmf keys can be forwarded with two party calls.
     */
    protected CallHandler otherCall;

    public CallHandler getOtherCall() {
        return this.otherCall;
    }

    public void suppressStatus(boolean suppressStatus) {
    this.suppressStatus = suppressStatus;
    }

    /*
     * Mostly for debugging
     */
    public String getCallState() {
    String s = "\n" + cp.toString();

    if (member != null) {
        s += "  ConferenceId: "
        + member.getConferenceManager().getId() + "\n";

        s += "\tStarted " + member.getTimeStarted() + "\n";
    } else {
        s += "\n";
    }

    if (csa != null) {
        s += "\tState = " + csa.getCallState() + "\n";
    } else {
        s += "\tNo Call Setup Agent" + "\n";
    }

    s += "\tIsDistributedBridge " + cp.isDistributedBridge() + "\n";


    if (cp.getCallTimeout() == 0) {
        s += "\tNo timeout\n";
    } else {
        s += "\tCall timeout in " + (cp.getCallTimeout() / 1000)
        + " seconds\n";
    }

    if (member != null) {
        s += " " + member.getMemberState();
    }

    return s;
    }

    public static String getCallStateForAllCalls() {
    String s = "";

    synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

        s += call.getCallState() + "\n";
        }
    }

    return s;
    }

    public static String getAllMixDescriptors() {
        String s = "";

        synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

        s += "MixDescriptors for " + call + "\n";
                s += call.getMember().getMixDescriptors() + "\n";
            }
        }

        return s;
    }

    public static String getAllAbbreviatedMixDescriptors() {
        String s = "";

        synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                s += "MixDescriptors for " + call + "\n";
                s += call.getMember().getAbbreviatedMixDescriptors() + "\n";
            }
        }

        return s;
    }

    /*
     * Overridden by OutgoingCallhandler.  There is no request handler
     * for incoming calls.
     */
    public abstract CallEventListener getRequestHandler();

    public CallParticipant getCallParticipant() {
    return cp;
    }

    /*
     * Used to switch a call from one conference to another.
     */
    public void setConferenceManager(ConferenceManager conferenceManager) {
    this.conferenceManager = conferenceManager;
    }

    public ConferenceManager getConferenceManager() {
    return conferenceManager;
    }

    public ConferenceMember getMember() {
    return member;
    }

    public MemberSender getMemberSender() {
    return memberSender;
    }

    public MemberReceiver getMemberReceiver() {
    return memberReceiver;
    }

    /*
     * This method is called by a CallSetupAgent once the endpoint
     * address is known.  The endpoint address is the address from which
     * we expect to receive RTP packets and to which we will send RTP packets.
     */
    public void setEndpointAddress(InetSocketAddress isa, byte mediaPayload,
            byte receivePayload, byte telephoneEventPayload) {

    setEndpointAddress(isa, mediaPayload, receivePayload, telephoneEventPayload, null);
    }

    public void setEndpointAddress(InetSocketAddress isa, byte mediaPayload,
            byte receivePayload, byte telephoneEventPayload, InetSocketAddress rtcpAddress) {

        member.initialize(this, isa, mediaPayload, receivePayload,
            telephoneEventPayload, rtcpAddress);
    }

    /*
     * true if call is established
     */
    public boolean isCallEstablished() {
        if (done || csa == null) {
            return false;
        }
        return csa.isCallEstablished();
    }

    /*
     * true is call is ending
     */
    public boolean isCallEnding() {
        if (done || csa == null) {
            return true;
        }
        return csa.isCallEnding();
    }

    public void addCallEventListener(CallEventListener listener) {
        synchronized (callEventListeners) {
            callEventListeners.add(listener);
        }
    }

    public void removeCallEventListener(CallEventListener listener) {
        synchronized (callEventListeners) {
            callEventListeners.remove(listener);
        }
    }

    public void sendCallEventNotification(CallEvent callEvent) {
    if (cp.getCallId() != null) {
        callEvent.setCallId(cp.getCallId());
    } else {
        callEvent.setCallId("CallIdNotInitialized");
    }

    callEvent.setConferenceId(cp.getConferenceId());

    callEvent.setCallInfo(cp.getCallOwner());

    if (csa != null) {
        callEvent.setCallState(csa.getCallState());
    } else {
        callEvent.setCallState(new CallState(CallState.UNINITIALIZED));
    }

        synchronized (callEventListeners)
        {
            for (int i = 0; i < callEventListeners.size(); i++)
            {
                CallEventListener listener = (CallEventListener) callEventListeners.elementAt(i);
                listener.callEventNotification(callEvent);
            }
        }
    }

    /*
     * The subclasses must override this.
     */
    public abstract void callEventNotification(CallEvent callEvent);

    /**
     * Send indication when speaker starts or stops speaking.
     */
    public static int totalSpeaking;

    public void speakingChanged(boolean isSpeaking) {
    if (isSpeaking) {
        totalSpeaking++;

        CallEvent callEvent =
        new CallEvent(CallEvent.STARTED_SPEAKING);

        callEvent.setStartedSpeaking();
        sendCallEventNotification(callEvent);
    } else {
        totalSpeaking--;

        CallEvent callEvent =
        new CallEvent(CallEvent.STOPPED_SPEAKING);

        callEvent.setStoppedSpeaking();
        sendCallEventNotification(callEvent);
    }
    }

    public static int getTotalSpeaking() {
    return totalSpeaking;
    }

    /**
     * Send indication when a dtmf key is pressed
     */
    public void dtmfKeys(String dtmfKeys) {
    //if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println(cp + " got dtmf keys " + dtmfKeys + " " 	+ cp.dtmfDetection());
    //}

    if (isCallEstablished()) {
        if (cp.dtmfDetection()) {
            member.stopTreatment(null);

        CallEvent callEvent = new CallEvent(CallEvent.DTMF_KEY);
        callEvent.setDtmfKey(dtmfKeys);
            sendCallEventNotification(callEvent);
        }

        if (otherCall != null) {
            Logger.println("Call " + cp + " forwarding dtmf key "  + dtmfKeys + " to " + otherCall);
            otherCall.getMemberSender().setDtmfKeyToSend(dtmfKeys);
        } else {
            getMemberSender().setDtmfKeyToSend(dtmfKeys);
        }
    } else {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println(cp + " Call not established, ignoring dtmf");
        }
        stopCallAnsweredTreatment();
    }
    }

    public void stopCallAnsweredTreatment() {
    if (done || csa == null) {
        return;
    }
    csa.stopCallAnsweredTreatment();
    }

    public void stopCallEstablishedTreatment() {
        if (done || csa == null) {
            return;
        }
        csa.stopCallEstablishedTreatment();
    }

    /*
     * terminate a call.
     */
    public void cancelRequest(String reason) {
    if (done) {
        return;
    }

    done = true;

    Logger.println(cp + " Cancel request " + reason);

        if (csa != null) {
            csa.cancelRequest(reason);
        }
    }

    /*
     * Add a treatment for the caller
     */
    public void addTreatment(TreatmentManager treatmentManager) {
    member.addTreatment(treatmentManager);
    }

    /*
     * unique call identifier incremented for each new call.
     */
    private static int callNumber = 0;

    public static synchronized String getNewCallId() {
    String s;

    do {
        callNumber++;
            s = String.valueOf(callNumber);

        String location = Bridge.getBridgeLocation();

        if (location.equalsIgnoreCase("Unknown") == false) {
            s += "_" + Bridge.getBridgeLocation();
        }
    } while (CallHandler.findCall(s) != null);

        return s;
    }

    /**
     * Find the new call of a call migration.
     */
    public static CallHandler findMigratingCall(String callId) {
    synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                CallParticipant cp = call.getCallParticipant();

                if (match(cp, callId) && cp.migrateCall()) {
                    if (Logger.logLevel >= Logger.LOG_DETAIL) {
                        Logger.println("findMigratingCall:  found " + callId);
                    }

                    return call;
                }
            }
        }
        return null;

    }

    /**
     * Find a call by callId.
     *
     * Calls are kept in the activeCalls list and uniquely identified
     * by <callId>::<name>@<phoneNumber> for a phone call and
     *
     * This method searches for a call with the callId.
     */
    public static CallHandler findCall(String callId) {
    if (Logger.logLevel >= Logger.LOG_DETAIL) {
        Logger.println("findCall:  looking for " + callId
        + ", " + activeCalls.size() + " active calls");
    }

    synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

        CallParticipant cp = call.getCallParticipant();

        if (Logger.logLevel >= Logger.LOG_DETAIL) {
            Logger.println("findCall:  looking for "
            + callId + " got " + cp.getCallId());
        }

        if (match(cp, callId)) {
            if (Logger.logLevel >= Logger.LOG_DETAIL) {
                Logger.println("findCall:  found " + callId);
            }

            return call;
        }
        }
    }
    return null;
    }

    private static boolean match(CallParticipant cp, String callId) {
    if (cp.getCallId().equals(callId)) {
        return true;
    }

    if (ConferenceManager.allowShortNames() == false) {
        return false;
    }

    String name = cp.getName();

    if (name != null) {
        if (name.equals(callId)) {
            return true;
        }

        name = name.replaceAll(" ", "_");

        if (name.equals(callId)) {
            return true;
        }
    }

    String number = cp.getPhoneNumber();

    if (number == null) {
        return false;
    }

    if (number.equals(callId)) {
        return true;
    }

    if (number.indexOf("sip:") == 0) {
        int ix = number.indexOf("@");

        if (ix >= 0) {
            number = number.substring(4, ix);

            if (number.equals(callId)) {
            return true;
            }
        }
    }

    return false;
    }

    /*
     * Add call to list of active calls
     */
    public void addCall(CallHandler callHandler) {
        synchronized(activeCalls) {
            activeCalls.add(callHandler);      // add to list of active calls
        }
    }

    /*
     * Remove call from list of active calls
     */
    public void removeCall(CallHandler callHandler) {
        synchronized(activeCalls) {
            activeCalls.remove(callHandler); // remove call from list

        Logger.println("");
            Logger.println("calls still in progress:  " + activeCalls.size());
        Logger.println("");
        }
    }

    /*
     * End all calls.
     */
    public static void shutdown() {
    shutdown(0);
    }

    public static void shutdown(int delaySeconds) {
    if (delaySeconds == 0) {
        /*
         * Quick shutdown right now!
         */
        hangup("0", "System shutdown");
        return;
    }

    /*
     * Notify the active calls that the MC bridge is shutting down
     */
    long start = System.currentTimeMillis();

    Logger.println("Shutting down in " + delaySeconds + " seconds");

    synchronized(activeCalls) {
        for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

        String id = call.getCallParticipant().getCallId();

        try {
                playTreatmentToCall(id, "joinBELL.au;shutdown.au;tts:" +
            delaySeconds + ";seconds.au");
        } catch (IOException e) {
            Logger.println("Can't play shutdown treatment to call "
            + id + " " + e.getMessage());
        }
        }

        /*
         * Wait at most a minute in case something is severly broken.
         */
        while (System.currentTimeMillis() - start < 60000) {
        boolean hasTreatments = false;

            for (int i = 0; i < activeCalls.size(); i++) {
                    CallHandler call = (CallHandler)activeCalls.elementAt(i);

            hasTreatments = call.getMember().hasTreatments();

            if (hasTreatments) {
            break;
            }
        }

        if (hasTreatments == false) {
            break;  // no treatments left to play
        }
        }
    }

    if (delaySeconds != 0) {
        int sleepTime = (int)((delaySeconds * 1000) -
        (System.currentTimeMillis() - start));

        if (sleepTime > 0) {
            try {
            Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
            }
        }
    }

    hangup("0", "System shutdown");
    }

    /*
     * Cancel a specified call.  If callid is 0, all calls are cancelled.
     */
    public static void hangup(String callId, String reason) {
    Vector callsToCancel = new Vector();

    synchronized(activeCalls) {
        /*
         * Make a list of all the calls we want to cancel, then cancel them.
         * We have to cancel them while not synchronized or
         * we could deadlock.
         */
        for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

        CallParticipant cp = call.getCallParticipant();

            if (callId.equals("0") || match(cp, callId)) {
            callsToCancel.add(call);
        }
        }
    }

    cancel(callsToCancel, reason, false);
    }

    public static void hangupOwner(String ownerId, String reason) {
        Vector callsToCancel = new Vector();

        synchronized(activeCalls) {
            /*
             * Make a list of all the calls we want to cancel, then cancel them.
             * We have to cancel them while not synchronized or
             * we could deadlock.
             */
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                CallParticipant cp = call.getCallParticipant();

                if (cp.getCallOwner().equals(ownerId)) {
                    callsToCancel.add(call);
                }
            }
        }

        cancel(callsToCancel, reason, false);
    }


    public static void suspendBridge() {
    cancel(activeCalls, "bridge suspended", true);
    }

    private static void cancel(Vector callsToCancel, String reason,
        boolean suppressStatus) {

    while (callsToCancel.size() > 0) {
            CallHandler call = (CallHandler)callsToCancel.remove(0);
        call.suppressStatus(suppressStatus);
        call.cancelRequest(reason);
    }
    }

    public String getReasonCallEnded() {
    return reasonCallEnded;
    }

    /*
     * Set cnThresh for the speech detector for a conference member.
     */
    public static void setCnThresh(String callId, int cnThresh) {
        synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                CallParticipant cp = call.getCallParticipant();

                if (match(cp, callId)) {
                    MemberReceiver memberReceiver = call.getMemberReceiver();

                    if (memberReceiver != null) {
                        memberReceiver.setCnThresh(cnThresh);
                    }
                }
            }
        }
    }

    /*
     * force packets to be dropped for debugging.
     */
    public static void setDropPackets(String callId, int dropPackets) {
    if (callId == null) {
        return;
    }

    synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

        CallParticipant cp = call.getCallParticipant();

        if (match(cp, callId)) {
            MemberReceiver memberReceiver = call.getMemberReceiver();

            if (memberReceiver != null) {
            memberReceiver.setDropPackets(dropPackets);
            }
                }
            }
        }
    }

    /**
     * Mute or unmute a conference member.
     */
    public void setMuted(boolean isMuted) {
    MemberReceiver memberReceiver = getMemberReceiver();

    if (memberReceiver != null) {
        memberReceiver.setMuted(isMuted);
    }
    }

    public static void setMuted(String callId, boolean isMuted) {
    if (callId == null) {
        return;
    }

    synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

        CallParticipant cp = call.getCallParticipant();

        if (match(cp, callId)) {
                    if (Logger.logLevel >= Logger.LOG_DETAIL) {
            String s = "";

                if (isMuted == false) {
                s = "un";
                }
                        Logger.println(cp.getCallId() + ":  " + s + "muted");
            }

            MemberReceiver memberReceiver = call.getMemberReceiver();

            if (memberReceiver != null) {
            memberReceiver.setMuted(isMuted);
            }
                }
        }
        }
    }

    public void setRemoteMediaInfo(String sdp) throws ParseException {
    csa.setRemoteMediaInfo(sdp);
    }

    public static void setRemoteMediaInfo(String callId, String sdp)
        throws ParseException {

        synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                CallParticipant cp = call.getCallParticipant();

                if (match(cp, callId)) {
            call.setRemoteMediaInfo(sdp);
            return;
                }
            }
        }

    throw new ParseException("Invalid callId: " + callId, 0);
    }

    /*
     * Say the number of calls in the conference
     */
    public int getNumberOfCalls() {
    return conferenceManager.getNumberOfMembers();
    }

    /**
     * Mute or unmute member in a whisperGroup
     */
    public static void setMuteWhisperGroup(String callId, boolean isMuted) {
        if (callId == null) {
            return;
        }

        synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                CallParticipant cp = call.getCallParticipant();

                if (match(cp, callId)) {
                    if (Logger.logLevel >= Logger.LOG_DETAIL) {
                        String s = "";

                        if (isMuted == false) {
                            s = "un";
                        }
                        Logger.println(cp.getCallId() + ":  " + s + "muted");
                    }

                    MemberReceiver memberReceiver = call.getMemberReceiver();

                    if (memberReceiver != null) {
                        memberReceiver.setMuteWhisperGroup(isMuted);
                    }
                }
            }
        }
    }

    /**
     * Mute or unmute a conference from a particular call.
     */
    public static void setConferenceMuted(String callId, boolean isMuted) {
        if (callId == null) {
            return;
        }

        synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                CallParticipant cp = call.getCallParticipant();

                if (match(cp, callId)) {
                    if (Logger.logLevel >= Logger.LOG_DETAIL) {
                        String s = "";

                        if (isMuted == false) {
                            s = "un";
                        }
                        Logger.println(cp.getCallId() + ":  conference " + s
                + "muted");
                    }

                    ConferenceMember member = call.getMember();

                    if (member!= null) {
                        member.setConferenceMuted(isMuted);
                    }
                }
            }
        }
    }

    /**
     * Mute or unmute the main conference from a particular call.
     */
    public static void setConferenceSilenced(String callId, boolean isSilenced) {
        synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                CallParticipant cp = call.getCallParticipant();

                if (match(cp, callId)) {
                    if (Logger.logLevel >= Logger.LOG_DETAIL) {
                        String s = "";

                        if (isSilenced == false) {
                            s = "un";
                        }

                        Logger.println(cp.getCallId()
                + ":  silenceMainonference " + s + "muted");
                    }

                    ConferenceMember member = call.getMember();

                    if (member!= null) {
                        member.setConferenceSilenced(isSilenced);
                    }
                }
            }
        }
    }

    /*
     * Set powerThresholdLimit for the speech detector for a member.
     */
    public static void setPowerThresholdLimit(String callId,
        double powerThresholdLimit) {

        synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                CallParticipant cp = call.getCallParticipant();

                if (match(cp, callId)) {
                    MemberReceiver memberReceiver = call.getMemberReceiver();

                    if (memberReceiver != null) {
                        memberReceiver.setPowerThresholdLimit(
                powerThresholdLimit);
                    }
                }
            }
        }
    }

    /**
     * set dmtfSuppression flag
     */
    private static boolean dtmfSuppression = true;

    public static void setDtmfSuppression(String callId,
        boolean dtmfSuppression) throws NoSuchElementException {

        if (callId.equals("0")) {
            CallHandler.dtmfSuppression = dtmfSuppression;
            return;
        }

    CallHandler callHandler = findCall(callId);

    if (callHandler == null) {
        throw new NoSuchElementException("Invalid callId specified:  "
        + callId);
    }

    callHandler.getCallParticipant().setDtmfSuppression(dtmfSuppression);
    }

    /**
     * Set flag to do voice detection while muted
     */
    public static void setVoiceDetectionWhileMuted(String callId,
        boolean voiceDetectionWhileMuted) {

        if (callId == null) {
            return;
        }

        synchronized(activeCalls) {
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

        CallParticipant cp = call.getCallParticipant();

                if (match(cp, callId)) {
            cp.setVoiceDetectionWhileMuted(voiceDetectionWhileMuted);

                    if (Logger.logLevel >= Logger.LOG_DETAIL) {
            Logger.println(cp.getCallId()
                + " voice detection while muted is "
                + voiceDetectionWhileMuted);
                    }
                }
            }
    }
    }

    /**
     * Get global dtmfSuppression flag
     */
    public static boolean dtmfSuppression() {
    return dtmfSuppression;
    }

    public static void setDoNotRecord(String callId, boolean doNotRecord)
        throws NoSuchElementException {

        CallHandler callHandler = findCall(callId);

        if (callHandler == null) {
        throw new NoSuchElementException("Invalid callId specified:  "
        + callId);
    }

        if (Logger.logLevel >= Logger.LOG_DETAIL) {
        String s = "";

        if (doNotRecord == true) {
        s = "NOT";
        }
            Logger.println(callHandler + ":  " + s + " okay to record");
    }

    callHandler.getMemberReceiver().setDoNotRecord(doNotRecord);
    }

    /**
     * Record data sent to or from a member
     */
    public static void recordMember(String callId, boolean enabled,
        String recordingFile, String recordingType, boolean fromMember)
        throws NoSuchElementException, IOException {

        CallHandler callHandler = findCall(callId);

        if (callHandler == null) {
        throw new NoSuchElementException("Invalid callId specified:  "
        + callId);
    }

    if (fromMember) {
        callHandler.getMemberReceiver().setRecordFromMember(enabled,
            recordingFile, recordingType);
    } else {
        callHandler.getMemberSender().setRecordToMember(enabled,
            recordingFile, recordingType);
    }
    }

    /**
     * Play a treament to a member
     */
    public static void playTreatmentToCall(String callId, String treatment)
        throws NoSuchElementException, IOException {

    playTreatmentToCall(callId, treatment, (TreatmentDoneListener) null);
   }

    public static void playTreatmentToCall(String callId, String treatment,
        double[] volume) throws NoSuchElementException, IOException {
    }

    public static void playTreatmentToCall(String callId, String treatment,
        TreatmentDoneListener treatmentDoneListener)
        throws NoSuchElementException, IOException {

        CallHandler callHandler = findCall(callId);

        if (callHandler == null) {
            throw new NoSuchElementException("Invalid callId specified:  "
        + callId);
        }

        if (callHandler.isCallEstablished() == false) {
            throw new IOException("Call is not ESTABLISHED:  " + callId);
        }

    callHandler.playTreatmentToCall(treatment, treatmentDoneListener);
    }

    public TreatmentManager playTreatmentToCall(String treatment)
        throws IOException {

    return playTreatmentToCall(treatment, (TreatmentDoneListener) null);
    }

    public TreatmentManager playTreatmentToCall(String treatment,  TreatmentDoneListener treatmentDoneListener)
        throws IOException {

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
        Logger.println("Playing treatment " + treatment + " to "
        + cp.getCallId());
    }

        TreatmentManager treatmentManager = new TreatmentManager(treatment, 0,
        conferenceManager.getMediaInfo().getSampleRate(),
        conferenceManager.getMediaInfo().getChannels());

    if (treatmentDoneListener != null) {
        treatmentManager.addTreatmentDoneListener(treatmentDoneListener);
    }

    addTreatment(treatmentManager);
    return treatmentManager;
    }

    /**
     * get the IP address and port used to receive packets for this call.
     */
    public InetSocketAddress getReceiveAddress() {
    return memberReceiver.getReceiveAddress();
    }

    /**
     * get the IP address and port used to send packets to this call.
     */
    public InetSocketAddress getSendAddress() {
    return memberSender.getSendAddress();
    }

    /**
     * Determine if this is the first member to join the conference.
     * This is called to determine if a special audio treatment
     * should be played.
     */
    public boolean isFirstMember() {
        return conferenceManager.isFirstMember();
    }

    /**
     * For debugging...
     */
    public static boolean tooManyDuplicateCalls(String phoneNumber) {
    synchronized(activeCalls) {
        int n = 0;

            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                CallParticipant cp = call.getCallParticipant();

        if (cp.getPhoneNumber().equals(phoneNumber)) {
            n++;
        }
        }

        if (n > duplicateCallLimit) {
        return true;
        }

        return false;
    }
    }

    public static void setDuplicateCallLimit(int duplicateCallLimit) {
    CallHandler.duplicateCallLimit = duplicateCallLimit;
    }

    public static int getDuplicateCallLimit() {
    return duplicateCallLimit;
    }

    public static void enablePSTNCalls(boolean enablePSTNCalls) {
    CallHandler.enablePSTNCalls = enablePSTNCalls;
    }

    public static boolean enablePSTNCalls() {
    return enablePSTNCalls;
    }

    /**
     * String representation of this Caller
     * @return the string representation of this Caller
     */
    public String toString() {
        return cp.toString();
    }

}
