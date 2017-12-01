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
import com.sun.voip.CallEventListener;
import com.sun.voip.Logger;
import com.sun.voip.TreatmentManager;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.SocketException;

import java.util.ArrayList;
import org.voicebridge.Application;

/**
 * Initiate a call, and join a conference
 *
 * This is a separate thread so that it monitor the call status.
 *
 * This class handles a	single party (joining a conference)
 * as well as two party calls.
 *
 * There is also support to try alternate gateways if one
 * gateway can't handle a new call.
 */
public class OutgoingCallHandler extends CallHandler implements CallEventListener {

    private CallEventListener csl;

    private Integer callInitiatedLock = new Integer(0);
    private Integer stateChangeLock = new Integer(0);
    private Integer waitCallAnswerLock = new Integer(0);
    private Integer waitCallEstablishedLock = new Integer(0);

    private boolean lastGateway = false;
    private boolean onlyOneGateway = false;

    public OutgoingCallHandler(CallEventListener callEventListener, CallParticipant cp) {

    addCallEventListener(this);
        csl = callEventListener;
        this.cp = cp;

    setName("Outgoing CallHandler for " + cp);
    }

    public CallEventListener getRequestHandler() {
        return csl;
    }

    /*
     * Thread to start a new call and join a conference.
     */
    private static int nCalls = 0;	// for debugging two gateways

    public void run() {
        /*
         * Join an existing conference or create a new one.
         */
        synchronized (ConferenceManager.getConferenceList()) {
            conferenceManager = ConferenceManager.getConference(cp);

            if (conferenceManager == null) {
                Logger.error("Couldn't start conference " + cp.getConferenceId());

                sendCallEventNotification( new CallEvent(CallEvent.CANT_START_CONFERENCE));
                return;
            }

            try {
                member = conferenceManager.joinConference(cp);
                memberSender = member.getMemberSender();
                memberReceiver = member.getMemberReceiver();
            } catch (IOException e) {
        CallEvent callEvent =
            new CallEvent(CallEvent.CANT_CREATE_MEMBER);

        callEvent.setInfo(e.getMessage());

                sendCallEventNotification(callEvent);
            removeCallEventListener(this);
                return;
            }
        }

    addCall(this);		// add to list of active calls

        lastGateway = false;
    onlyOneGateway = false;

        /*
         * Start the call (INVITE) and wait for it to end (BYE).
         */
        ArrayList voIPGateways = SipServer.getVoIPGateways();
        String gateway = cp.getVoIPGateway();

        if (gateway != null) {
            /*
             * User specified a specific gateway.  Use that one only.
             */
            Logger.println("Call " + this + ":  Using gateway specified for the call:  " + gateway);

            lastGateway = true;
        onlyOneGateway = true;
            placeCall();
        } else if (voIPGateways.size() > 0) {

            if (voIPGateways.size() == 1) {
                onlyOneGateway = true;
            }

            lastGateway = true;
            placeCall();

        } else if (cp.getPhoneNumber() != null && cp.getPhoneNumber().indexOf("sip:") == 0) {

             placeCall(); // no gateway involved, direct SIP call

        } else if (cp.getProtocol() != null && ("Speaker".equals(cp.getProtocol()) || "WebRtc".equals(cp.getProtocol()) || "Rtmfp".equals(cp.getProtocol()))) {

             placeCall(); // WebRtc call

        } else {

            Logger.error("Couldn't place call " + cp);
            sendCallEventNotification( new CallEvent(CallEvent.CANT_START_CONFERENCE));
        }

        conferenceManager.leave(member); // Remove member from conference.

        removeCall(this);		 // remove call from active call list
    removeCallEventListener(this);
        done = true;
    }

    private void placeCall() {
    String protocol = Bridge.getDefaultProtocol();

    if (cp.getProtocol() != null) {
        protocol = cp.getProtocol();
    }

    if (protocol.equalsIgnoreCase("SIP")) {
            csa = new SipTPCCallAgent(this);
    } else if (protocol.equalsIgnoreCase("NS")) {
        csa = new NSOutgoingCallAgent(this);
    } else if (protocol.equalsIgnoreCase("WebRtc")) {
        csa = new WebRtcCallAgent(this);
    } else if (protocol.equalsIgnoreCase("Speaker")) {
        csa = new SpeakerCallAgent(this);
    } else if (protocol.equalsIgnoreCase("Rtmfp")) {
        csa = new RtmfpCallAgent(this);
    } else {
        //csa = new H323TPCCallAgent(this);
        reasonCallEnded =
        CallEvent.getEventString(CallEvent.H323_NOT_IMPLEMENTED);

        sendCallEventNotification(
        new CallEvent(CallEvent.H323_NOT_IMPLEMENTED));

        Logger.println("Call " + cp + ":  " + reasonCallEnded);
        return;
    }

        try {
            csa.initiateCall();

        synchronized (callInitiatedLock) {
            callInitiatedLock.notifyAll();
        }

            synchronized(stateChangeLock) {
                if (reasonCallEnded == null) {

            //if (protocol.equalsIgnoreCase("SIP") == false) {
            //    /*
            //     * Leave Conference and rejoin with the right local media parameters
            //     * XXX Need to somehow get the socket from the h323 stack!
            //     */
            //    member.getMemberReceiver().setReceiveSocket();
            //    conferenceManager.transferMember(conferenceManager, member);
            //}

                    try {
                        stateChangeLock.wait();	// wait for call to end
                    } catch (InterruptedException e) {
                    }
                }
            }
        } catch (IOException e) {
        synchronized (callInitiatedLock) {
            callInitiatedLock.notifyAll();
        }

        if (reasonCallEnded == null) {
        cancelRequest(e.getMessage());
        }

            Logger.println("Call " + this + " Exception " + e.getMessage());
        }
    }

    /*
     * This method is called where there is new status information.
     * Status can be a state change, dtmf key pressed,
     * or speaking not speaking notification.
     */
    public void callEventNotification(CallEvent callEvent) {
    if (Logger.logLevel >= Logger.LOG_INFO) {
        Logger.println("Notification:  " + callEvent);
    }

    if (callEvent.equals(CallEvent.STATE_CHANGED)) {
        if (callEvent.getCallState().equals(CallState.ANSWERED)) {

                /*
                 * For two party calls
                 */
                synchronized(waitCallAnswerLock) {
                    waitCallAnswerLock.notify();
                }
            } else if (callEvent.getCallState().equals(CallState.ESTABLISHED)) {
                /*
                 * For migrating calls
                 */
                synchronized(waitCallEstablishedLock) {
                    waitCallEstablishedLock.notify();
                }
            } else if (callEvent.getCallState().equals(CallState.ENDING)) {
                CallHandler callHandler =
                    CallHandler.findMigratingCall(cp.getCallId());

                if (callHandler == this) {
                    /*
                     * If it's a gateway error and it's not the last gateway,
                     * don't end the call.  It will be retried with the
                     * alternate gateway.
                     */
                    if (callEvent.getInfo().indexOf("gateway error") >= 0 &&
                            lastGateway == false) {

                        return;
                    }

            callEvent = new CallEvent(CallEvent.MIGRATION_FAILED);

            callEvent.setInfo("Migration failed: " + getReasonCallEnded());
                    sendCallEventNotification(callEvent);
                }
            } else if (callEvent.getCallState().equals(CallState.ENDED)) {
                reasonCallEnded = callEvent.getInfo();

                synchronized(waitCallAnswerLock) {
                    waitCallAnswerLock.notify();
                }

                if (reasonCallEnded.indexOf("gateway error") >= 0 &&
                        lastGateway == false) {

                    CallHandler callHandler =
                        CallHandler.findMigratingCall(cp.getCallId());

                    if (callHandler == this) {
                        synchronized(stateChangeLock) {
                            /*
                             * Let the outgoing call handler know so
                             * it can try another gateway.
                             */
                            stateChangeLock.notify();
                        }
                        return;	// don't tell the migrator yet
                    }
                }

                synchronized(waitCallEstablishedLock) {
                    waitCallEstablishedLock.notify();
                }

                synchronized(stateChangeLock) {
                    stateChangeLock.notify();	// the call has ended
                }

                /*
                 * If it's a gateway error and not the last gateway,
                 * don't end the call.  It will be retried with the
                 * alternate gateway.
                 */
                if (reasonCallEnded.indexOf("gateway error") >= 0 &&
                        lastGateway == false) {

                    return;
                }

                cancelRequest(reasonCallEnded);
            }
    }

        if (suppressEvent(cp, callEvent) == false) {
            Application.outgoingCallNotification(callEvent);

        if (csl != null) csl.callEventNotification(callEvent);


        }
    }

    /*
     * This method is called by a CallSetupAgent once the endpoint
     * address is known.  The endpoint address is the address from which
     * we expect to receive RTP packets and to which we will send RTP packets.
     */
    //public void setEndpointAddress(InetSocketAddress isa, byte mediaPayload,
    //	    byte receivePayload, byte telephoneEventPayload) {
    //
    //    member.initialize(this, isa, mediaPayload, receivePayload, telephoneEventPayload);
    //}

    /*
     * To make call migration and automatic retries to alternate gateways
     * transparent to the facilitator, we need to suppress certain
     * status messages.
     */
    private boolean suppressEvent(CallParticipant cp, CallEvent callEvent) {
        /*
         * Suppress status from migrated calls so the facilitator
         * doesn't see CALL_ENDED from the previous call.
         *
         * XXX Not sure about this.  I think we want the status to go through.
         * The receiver of the status will see "migrated" in the message
         * and can decide what to do.
         *
         * For the new call, we allow "No Answer".  Once the call is answered
         * we clear the migrateCall flag so that CALL_ENDING and CALL_END
         * will be delivered to the client
         */
        if (suppressStatus == true) {
            if (callEvent.getInfo() != null &&
            callEvent.getInfo().indexOf("No Answer") >= 0 ||
            callEvent.equals(CallEvent.BUSY_HERE) ||
            callEvent.equals(CallEvent.CALL_ANSWER_TIMEOUT) ||
            callEvent.equals(CallEvent.MIGRATED) ||
            callEvent.equals(CallEvent.MIGRATION_FAILED) ||
            callEvent.equals(CallEvent.JOIN_TIMEOUT)) {

                return false;
            }

            return true;
        }

        /*
         * We automatically retry calls with an alternate gateway
         * when there is a gateway error.  The status sent to the
         * socket should make the switch to the alternate gateway transparent.
         * We don't want to send CALL_ENDING or CALL_ENDED until
         * we've tried the alternate gateway.
         * We also want to suppress CALL_PARTICIPANT_INVITED when
         * trying the alternate gateway.
         */
        if (lastGateway == false) {
            /*
             * Suppress gateway errors from default gateway
             */
            if (callEvent.getInfo().indexOf("gateway error") >= 0) {
                return true;
            }

            return false;
        }

        /*
         * Suppress CALL_PARTICIPANT_INVITED message from alternate gateway
         */
    if (onlyOneGateway == false && callEvent.equals(CallEvent.STATE_CHANGED) &&
            callEvent.getCallState().equals(CallState.INVITED)) {

            return true;
        }

        /*
         * No need to suppress this message.
         */
        return false;
    }

    /*
     * terminate a call.
     */
    public void cancelRequest(String reason) {
        done = true;

        if (csa != null) {
            CallHandler migratingCall =
                    CallHandler.findMigratingCall(cp.getCallId());

            if (migratingCall == this) {
                Logger.println("Failed to Migrate:  " + reason);
            }

            csa.cancelRequest(reason);
        }

        synchronized(waitCallAnswerLock) {
            waitCallAnswerLock.notify();
        }

        CallHandler otherCall = this.otherCall;

        this.otherCall = null;

        if (otherCall != null) {
            Logger.println("otherCall is " + otherCall.getCallParticipant());

            otherCall.cancelRequest("Two party call ended");
        }
    }

    public String getSdp() {
    synchronized (callInitiatedLock) {
            while (csa == null && !done && reasonCallEnded == null) {
        try {
            callInitiatedLock.wait();
        } catch (InterruptedException e) {
        }
        }
        return csa.getSdp();
    }
    }

    /*
     * For two party calls.
     *
     * When one party hangs up, the other call should be terminated as well.
     */
    //private OutgoingCallHandler otherCall;

    public void setOtherCall(OutgoingCallHandler otherCall) {
        this.otherCall = otherCall;
    }

    /*
     * For two party calls, we wait until the first party answers
     * before calling the second party.
     *
     * When the first party answers, the second party is called and
     * the treatment is played to the first party.
     *
     * When the second party answers, the treatment to the first party
     * is stopped.
     */
    public boolean waitForCallToBeAnswered() {

        String protocol = Bridge.getDefaultProtocol();

        if (cp.getProtocol() != null) {
            protocol = cp.getProtocol();
        }

        if (protocol.equalsIgnoreCase("WebRtc") || protocol.equalsIgnoreCase("Rtmfp")  || protocol.equalsIgnoreCase("Speaker")) {
            return true;
        }

        synchronized(waitCallAnswerLock) {
            if (done || reasonCallEnded != null) {
                return false;
            }

            try {
                waitCallAnswerLock.wait();
            } catch (InterruptedException e) {
            }
        }

        if (done || reasonCallEnded != null) {
            return false;
        }

        return true;
    }

    public boolean waitForCallToBeEstablished() {

        if (cp.getProtocol().equalsIgnoreCase("WebRtc") || cp.getProtocol().equalsIgnoreCase("Rtmfp") || cp.getProtocol().equalsIgnoreCase("Speaker")) {
            return true;
        }

        synchronized(waitCallEstablishedLock) {
            if (done || reasonCallEnded != null) {
                return false;
            }

            try {
                waitCallEstablishedLock.wait();
            } catch (InterruptedException e) {
            }
        }

        if (done || reasonCallEnded != null) {
            return false;
        }

        return true;
    }

    /*
     * Cancel all calls started by the specified requestHandler
     */
    public static void hangup(CallEventListener callEventListener, String reason) {

        ArrayList<CallHandler> callsToCancel = new ArrayList();

        synchronized(activeCalls) {
            /*
             * Make a list of all the calls we want to cancel, then cancel them.
             * We have to cancel them while not synchronized or
             * we could deadlock.
             */
            for (int i = 0; i < activeCalls.size(); i++) {
                CallHandler call = (CallHandler)activeCalls.elementAt(i);

                if (call.getRequestHandler() == callEventListener) {
                    callsToCancel.add(call);
                }
            }
        }

        cancel(callsToCancel, reason);
    }

    private static void cancel(ArrayList<CallHandler> callsToCancel, String reason) {
        while (callsToCancel.size() > 0) {
            CallHandler call = callsToCancel.remove(0);
            call.cancelRequest(reason);
        }
    }

    /**
     * String representation of this OutgoingCallHandler
     * @return the string representation of this OutgoingCallHandler
     */
    public String toString() {
        return cp.toString();
    }

}
