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
import com.sun.voip.CallState;
import com.sun.voip.CallEvent;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.TreatmentDoneListener;
import com.sun.voip.TreatmentManager;

import java.io.IOException;

import java.net.InetSocketAddress;

import java.text.ParseException;

import java.util.NoSuchElementException;

/**
 * Super class with code common to both Sip User Agents and 
 * non-SIP Agents.
 * 
 * This class is responsible for playing treatments, getting notification
 * when done, managing the state of active calls.
 */
public class CallSetupAgent implements TreatmentDoneListener, Runnable {

    protected TreatmentManager callAnsweredTreatment = null;

    private TreatmentManager callEstablishedTreatment = null;

    protected String reasonCallTerminated;

    protected CallState callState = new CallState();

    protected CallHandler callHandler;
    protected CallParticipant cp;

    private Thread inviteTimeoutThread;
    private static int defaultCallAnswerTimeout = 90;  // 90 seconds

    /**
     * Constructor
     * @param callHandler CallHandler this CallParticipant is associated with
     * @param cp the CallParticipant
     */
    public CallSetupAgent(CallHandler callHandler) {
    this.callHandler = callHandler;

    cp = callHandler.getCallParticipant();
    }
    
    /*
     * INVITE timeout thread, handle call not answered
     */
    public void run() {
        int timeout = cp.getCallAnswerTimeout();

        if (timeout == 0) {
            timeout = defaultCallAnswerTimeout;
        }

        try {
            Thread.sleep(timeout * 1000);
        } catch (InterruptedException e) {
        }

    inviteTimeoutThread = null;

        if (reasonCallTerminated == null && getState() < CallState.ANSWERED) {
        Logger.println("Call answer time out " + cp);
        sendCallEventNotification(
        new CallEvent(CallEvent.CALL_ANSWER_TIMEOUT));
            cancelRequest("No answer");
        }
    }

    /**
     * set the call answer timeout in seconds
     */
    public static void setDefaultCallAnswerTimeout(
        int defaultCallAnswerTimeout) {

        CallSetupAgent.defaultCallAnswerTimeout = defaultCallAnswerTimeout;
    }

    /**
     * Get the call answer timeout in seconds
     */
    public static int getDefaultCallAnswerTimeout() {
        return defaultCallAnswerTimeout;
    }

    public CallState getCallState() {
    return callState;
    }

    public int getState() {
    return callState.getState();
    }

    protected void setState(int state) {
    setState(state, null);
    }

    protected void setState(int state, String info) {
    callState = new CallState(state);

    CallEvent callEvent = new CallEvent(CallEvent.STATE_CHANGED);

    callEvent.setCallState(callState);

    String s = "";

    if (state == CallState.INVITED || state == CallState.ESTABLISHED) {
        s += "ConferenceReceiverPort='" 
        + callHandler.getReceiveAddress().getPort() + "'";

        MediaInfo mediaInfo =
                 callHandler.getConferenceManager().getMediaInfo();

            s += " ConferencePayload='" +  mediaInfo.getPayload() + "'";
        s += " BridgeIPAddress='" 
        + Bridge.getPrivateHost() + "'";

        s += " BridgeInfo='" 
                + Bridge.getPrivateHost() + ":"
                + Bridge.getPrivateControlPort() + ":"
                + Bridge.getPrivateSipPort() + ":" 
        + Bridge.getPublicHost() + ":"
                + Bridge.getPublicControlPort()+ ":"
                + Bridge.getPublicSipPort() + "'";
    } 

    if (info != null) {
        s = info + " " + s;
    }

    callEvent.setInfo(s);

    Logger.println("Call " + callHandler + " " + callState);

    sendCallEventNotification(callEvent);

        if (state == CallState.ESTABLISHED) {
            String treatment = cp.getCallEstablishedTreatment();

            if (treatment != null) {
        callEstablishedTreatment = initializeTreatment(treatment, 0);

        if (callEstablishedTreatment != null) {
            addTreatment(callEstablishedTreatment);
        }
            }
    }

    if (inviteTimeoutThread == null && state == CallState.INVITED) {
        inviteTimeoutThread = new Thread(this);
            inviteTimeoutThread.start();        // start invite timeout thread
    }
    }

    protected void sendCallEventNotification(CallEvent callEvent) {
    callHandler.sendCallEventNotification(callEvent);
    }

    /*
     * Send the address of the endpoint to the CallHandler so 
     * the CallHandler can tell the Mixer where to send RTP data.
     */
    protected void setEndpointAddress(InetSocketAddress isa, 
        byte mediaPayload, byte receivePayload,
        byte telephoneEventPayload) {

    setEndpointAddress(isa, mediaPayload, receivePayload, telephoneEventPayload, null);
    }

    protected void setEndpointAddress(InetSocketAddress isa, 
        byte mediaPayload, byte receivePayload,
        byte telephoneEventPayload, InetSocketAddress rtcpAddress) {

    callHandler.setEndpointAddress(isa, mediaPayload, receivePayload,
        telephoneEventPayload, rtcpAddress);
    }

    /*
     * true if call is established
     */
    public boolean isCallEstablished() {
    return getState() == CallState.ESTABLISHED || 
        getState() == CallState.ENDING;
    }

    public boolean isCallEnding() {
    return getState() == CallState.ENDING || 
        getState() == CallState.ENDED;
    }

    /**
     * Setup the call answerered treatment manager
     */
    protected void initializeCallAnsweredTreatment() {
        String treatment = cp.getCallAnsweredTreatment();

        int repeatCount = 0;

        if (cp.getJoinConfirmationTimeout() != 0) {
            /*
             * If join confirmation is requested, we will repeat
             * the message.  This is needed because AccessLine
             * answers the call before a person actually gets connected
             * to hear the message.
             */
            repeatCount = 30;
        }

    callAnsweredTreatment = initializeTreatment(treatment, repeatCount);

        if (callHandler.isFirstMember()) {
            treatment = cp.getFirstConferenceMemberTreatment();

            if (treatment != null) {
                callAnsweredTreatment =
            initializeTreatment(treatment, repeatCount);
            }
        }
    }

    private TreatmentManager initializeTreatment(String treatment,
        int repeatCount) {

    MediaInfo mediaInfo = callHandler.getConferenceManager().getMediaInfo();

        if (treatment != null) {
        try {
                return new TreatmentManager(
                    treatment, repeatCount, mediaInfo.getSampleRate(),
            mediaInfo.getChannels());
        } catch (IOException e) {
        Logger.println("can't play treatment " + treatment
            + " " + e.getMessage());
        }
        }
    return null;
    }

    /*
     * Add a treatment to play to the call.
     */
    protected void addTreatment(TreatmentManager treatmentManager) {
    callHandler.addTreatment(treatmentManager);
    }

    /**
     * Start call answered treatment
     */
    protected void startCallAnsweredTreatment() {
        Logger.println("Call " + callHandler 
        + " starting call answered treatment");

        callAnsweredTreatment.addTreatmentDoneListener(this);
        callHandler.addTreatment(callAnsweredTreatment);
    }

    /**
     * Stop call answered treatment
     */
    public void stopCallAnsweredTreatment() {
    if (callAnsweredTreatment != null) {
            if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + callHandler 
            + " Stop callAnsweredTreatment player...");
        }
    
        callAnsweredTreatment.stopTreatment();
        callAnsweredTreatment = null;
    } else {
        if (cp.getJoinConfirmationTimeout() != 0) {
        if (getState() == CallState.INVITED) {
            setState(CallState.ANSWERED);
        
            MediaInfo mediaInfo = 
            callHandler.getConferenceManager().getMediaInfo();

            setState(CallState.ESTABLISHED,
            "ConferencePayload='" +  mediaInfo.getPayload() + "'"
                + " BridgeIPAddress='" 
            + Bridge.getPrivateHost() + "'");
        }
        }
    }
    }

    public void stopCallEstablishedTreatment() {
        if (callEstablishedTreatment == null) {
        return;
    }

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + callHandler
                + " Stop callEstablishedTreatment player...");
        }

        callEstablishedTreatment.stopTreatment();
        callEstablishedTreatment = null;
    }

    /**
     * Notification that a treatment has finished
     *
     * The state we are in determines what we do next.
     */
    public void treatmentDoneNotification(TreatmentManager treatmentManager) {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + callHandler 
        + " treatment done notification, current state "
                + callState + " " + callHandler);
        }

        switch (getState()) {
        case CallState.INVITED:
            /*
             * When join confirmation is requested, we stay in the
             * CALL_PARTICIPANT_INVITED state until a dtmf key is pressed.
             * Then we change state to CALL_PARTICIPANT_ANSWERED and
             * fall through.
         *
         * XXX We need to make sure the treatment repeats enough times.
         * If it finishes before we timeout, we'll treat the call as answered!
         *
             */
        if (reasonCallTerminated != null) {
        break;
        }

            setState(CallState.ANSWERED);
        
        case CallState.ANSWERED:
        /*
         * Call answered treatment is done, we're ready for the conference
         */
        MediaInfo mediaInfo = 
        callHandler.getConferenceManager().getMediaInfo();

        setState(CallState.ESTABLISHED,
        "ConferencePayload='" +  mediaInfo.getPayload() + "'"
        + " BridgeIPAddress='" 
        + Bridge.getPrivateHost() + "'");
            break;

    case CallState.ENDING:
        /*
         * Call end treatment is done, time to end the call
         */
        terminateCall();
        done();
        break;

    default:
        Logger.error("Call " + callHandler 
        + ":  unexpected state " + callState);
            break;
        }
    }

    /**
     * A CallSetupAgent may override the following methods:
     *
     * initiateCall() to start a call
     * terminateCall() class specific code to end a call.
     */
    public void initiateCall() throws IOException {
    }

    public String getSdp() {
    return null;
    }

    public void setRemoteMediaInfo(String sdp) throws ParseException {
    }

    public void terminateCall() {
    }

    /**
     * Cancel a call
     */
    public void cancelRequest(String s) {
    if (reasonCallTerminated != null || getState() == CallState.ENDED) {
        return;
    }

    reasonCallTerminated = s;

        if (inviteTimeoutThread != null) {
            inviteTimeoutThread.interrupt();
            inviteTimeoutThread = null;
        }

    if (Logger.logLevel >= Logger.LOG_INFO) {
            Logger.println(
        "Call " + callHandler + ":  cancelling call, " + s);
    }

    if (callAnsweredTreatment != null) {
        callAnsweredTreatment.stopTreatment();
    }

    if (callEstablishedTreatment != null) {
        callEstablishedTreatment.stopTreatment();
    }

    if (getState() == CallState.ESTABLISHED) {
        String endTreatment = cp.getCallEndTreatment();

        if (endTreatment != null && cp.isConferenceMuted() == false) {
        try {
                MediaInfo mediaInfo = 
            callHandler.getConferenceManager().getMediaInfo();

                TreatmentManager callEndTreatment =
            new TreatmentManager(endTreatment, 0, 
                mediaInfo.getSampleRate(), mediaInfo.getChannels());

            setState(CallState.ENDING, "Reason='" + s + "'");

            if (Logger.logLevel >= Logger.LOG_MOREINFO) {
                    Logger.println("Call " + callHandler 
                + " adding end treatment...");
            }

            callEndTreatment.addTreatmentDoneListener(this);
                    callHandler.addTreatment(callEndTreatment);

            return;
        } catch (IOException e) {
            Logger.error("Call " + callHandler + " " + e.getMessage());
        }
        }
    } 

    terminateCall();	// do subclass specific work
    setState(CallState.ENDING, "Reason='" + s + "'");
    done();
    }

    /*
     * Finish the call termination process
     */
    private void done() {
    if (getState() == CallState.ENDED) {
        return;
    }

    setState(CallState.ENDED, "Reason='" + reasonCallTerminated + "'");
    }

}
