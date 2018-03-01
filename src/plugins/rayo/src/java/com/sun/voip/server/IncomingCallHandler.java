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
import com.sun.voip.TreatmentManager;
import com.sun.voip.Logger;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.NoSuchElementException;
import java.text.ParseException;

import java.util.Vector;
import java.util.Collection;
import org.voicebridge.Application;
import org.voicebridge.Config;

import org.ifsoft.rayo.RayoComponent;


/**
 * Handle an incoming call.  The call is placed into a temporary conference.
 * Based on dtmf (or voice) input from the caller, the call is transferred
 * to the target conference.
 *
 * This is a separate thread so that it can monitor the call status.
 */
public class IncomingCallHandler extends CallHandler
    implements CallEventListener {

    private Integer stateChangeLock = new Integer(0);
    private ConferenceManager newConferenceManager;
    private TreatmentManager treatmentManager;
    private Object requestEvent;
    boolean haveIncomingConferenceId = false;;
    private static String defaultIncomingConferenceId = "IncomingCallsConference";
    private static String incomingCallTreatment;
    private static boolean incomingCallVoiceDetection = false;
    private static boolean directConferencing = false;
    private IncomingConferenceHandler incomingConferenceHandler;

    public IncomingCallHandler(CallEventListener listener,
       CallParticipant cp) {

    this(listener, cp, null);
    }

    public IncomingCallHandler(CallParticipant cp, Object requestEvent) {
    this(null, cp, requestEvent);
    }

    public IncomingCallHandler(CallEventListener listener, CallParticipant cp, Object requestEvent)
    {
        System.out.println("IncomingCallHandler " + cp);

        if (CallHandler.enablePSTNCalls() == false) {
            Logger.println("Ignoring incoming call " + cp.getToPhoneNumber());
            return;
        }

        if (listener != null) {
            addCallEventListener(listener);
        }

        this.cp = cp;
        this.requestEvent = requestEvent;

        addCallEventListener(this);

        if (directConferencing)
        {
            if (cp.getConferenceId() == null || cp.getConferenceId().length() == 0)
            {
                System.out.println("Don't have conf, using default....");
                cp.setConferenceId(defaultIncomingConferenceId); // wait in lobby

            } else {

                Logger.println("Have conf " + cp.getConferenceId());
                haveIncomingConferenceId = true; // goto your conference
            }

            start();

        } else {

            System.out.println("Incoming SIP, call " + cp);

            if (RayoComponent.self.routeIncomingSIP(cp))
            {
                haveIncomingConferenceId = true;
                start();

            } else {
                //  conf bridge

                if (Config.getInstance().getConferenceExten().equals(cp.getToPhoneNumber()))
                {
                    incomingConferenceHandler = new IncomingConferenceHandler(this, cp.getToPhoneNumber());
                    start();

                } else if (Config.getInstance().getConferenceByPhone(cp.getToPhoneNumber()) != null) {

                    incomingConferenceHandler = new IncomingConferenceHandler(this, cp.getToPhoneNumber());
                    start();

                } else {
                    cancelRequest(cp.getToPhoneNumber() + " is not a valid endpoint");	// reject call
                }
            }

        }
    }

    public static void setDirectConferencing(boolean directConferencing) {
    IncomingCallHandler.directConferencing = directConferencing;
    }

    public static boolean getDirectConferencing() {
    return directConferencing;
    }

    public void cancelRequest(String s)
    {
        super.cancelRequest(s);

        CallHandler otherCall = this.otherCall;

        this.otherCall = null;

        if (otherCall != null) {
            Logger.println("otherCall is " + otherCall.getCallParticipant());

            otherCall.cancelRequest("Bridged Call ended");
        }
    }

    class TransferTimer extends Thread
    {
        private ConferenceMember member;
        private String conferenceId;

        private static final int TRANSFER_TIMEOUT = 3 * 60 * 1000;

        public TransferTimer(ConferenceMember member)
        {
            this.member = member;
            conferenceId = member.getCallParticipant().getConferenceId();
            start();
        }

        public void run() {
            try {
                Thread.sleep(TRANSFER_TIMEOUT);
            } catch (InterruptedException e) {

            }

            if (!done && member != null)
            {
                if (member.getCallParticipant().getConferenceId().indexOf(defaultIncomingConferenceId) == 0) {

                    Logger.println("Incoming call " + member + " call transfer timedout");
                    cancelRequest("Incoming call wasn't transferred");
                }
            }
        }
    }

    /*
     * Thread to process this incoming call.
     * Create a temporary conference and add this call.
     */
    public void run()
    {
        if (haveIncomingConferenceId == false) {
            cp.setConferenceId(defaultIncomingConferenceId);
        }

        synchronized (ConferenceManager.getConferenceList())
        {
            conferenceManager =
                ConferenceManager.getConference(cp.getConferenceId());

            if (conferenceManager == null) {
                Logger.error("Couldn't start conference "
                    + cp.getConferenceId());

                sendCallEventNotification(new CallEvent(CallEvent.CANT_START_CONFERENCE));

                return;
            }

            cp.setDisplayName(cp.getName());
            cp.setDtmfDetection(true);

            if (cp.getCallId() == null) {
                cp.setCallId(getNewCallId());
            }

            cp.setCallAnsweredTreatment(incomingCallTreatment);
            cp.setVoiceDetection(incomingCallVoiceDetection);

            if (haveIncomingConferenceId == false) {
                cp.setWhisperGroupId(cp.getCallId());
                cp.setMuted(true);
            }

            if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println(cp.getCallSetupRequest());
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
                return;
            }

            Logger.println("Incoming Call " + cp + " joined conference "
            + cp.getConferenceId());
        }

        addCall(this);    // add to list of active calls

        String protocol = Bridge.getDefaultProtocol();

        if (cp.getProtocol() != null) {
            protocol = cp.getProtocol();
        }

        if (protocol.equalsIgnoreCase("NS")) {
            csa = new NSIncomingCallAgent(this);

            try {
                csa.initiateCall();

            } catch (IOException e) {

                Logger.println("initiateCall failed:  " + e.getMessage());

                CallEvent callEvent =
                    new CallEvent(CallEvent.CANT_CREATE_MEMBER);

                callEvent.setInfo(e.getMessage());
                    sendCallEventNotification(callEvent);
                        return;
            }

        } else if (protocol.equalsIgnoreCase("SIP")) {

            csa = new SipIncomingCallAgent(this, requestEvent);

        } else {
                // XXX csa = new H323Agent(this);
                Logger.println("H.323 support isn't implemented yet!");

                sendCallEventNotification(new CallEvent(CallEvent.H323_NOT_IMPLEMENTED));
                return;
        }

        if (haveIncomingConferenceId == false) {
            new TransferTimer(member);
        }

        synchronized(stateChangeLock)
        {
            if (csa.getState() != CallState.ENDED)
            {
                try {
                    Logger.println("Call " + cp + " Waiting for call to end...");
                    stateChangeLock.wait();	// wait for call to end

                } catch (InterruptedException e) {

                }
            }
        }

        try {
            Logger.println("Call " + cp + " ended...");
            conferenceManager.leave(member); // Remove member from conference.
        } catch (Exception e) {
            e.printStackTrace();
        }

        Logger.println("Call " + cp + " removed...");
        removeCall(this);  // remove from list of active calls

        csa = null;
        cancelRequest("Incoming call ended");

        done = true;
    }

    public static void setIncomingCallTreatment(String treatment) {
    incomingCallTreatment = treatment;
    }

    public static String getIncomingCallTreatment() {
    return incomingCallTreatment;
    }

    public static void setIncomingCallVoiceDetection(
        boolean incomingCallVoiceDetection) {

    IncomingCallHandler.incomingCallVoiceDetection =
        incomingCallVoiceDetection;
    }

    public static boolean getIncomingCallVoiceDetection() {
    return incomingCallVoiceDetection;
    }

    private String lastDtmfKey = "";
    private boolean speakDtmf = false;

    public void callEventNotification(CallEvent callEvent) {


    Logger.println("IncomingCallHandler " + callEvent.toString());

    if (callEvent.equals(callEvent.STATE_CHANGED) &&
            callEvent.getCallState().equals(CallState.ESTABLISHED)) {

        if (incomingCallTreatment != null) {

            try {
                playTreatmentToCall(incomingCallTreatment);
            } catch (IOException e) {
                Logger.println(
                "Unable to play incomingCallTreatment "
                + incomingCallTreatment);
            }
        }
    } else if (callEvent.equals(CallEvent.DTMF_KEY)) {
        member.stopTreatment(null);

        String dtmf = callEvent.getDtmfKey();

        if (lastDtmfKey.equals("*") && dtmf.equals("*")) {
        speakDtmf = !speakDtmf;
        }

        lastDtmfKey = dtmf;

        if (speakDtmf == true) {
            speakDtmf(dtmf);
        }
    } else if (callEvent.equals(callEvent.STATE_CHANGED) && callEvent.getCallState().equals(CallState.ENDED)) {

        Logger.println("Call " + cp + " Got ENDED status.");

        synchronized(stateChangeLock) {
        stateChangeLock.notify();  // the call has ended
        }
    }

    Application.incomingCallNotification(callEvent);
    }



    private void speakDtmf(String dtmf) {
       for (int i = 0; i < dtmf.length(); i++) {
           String s = dtmf.substring(i, i + 1);

       try {
           if (s.equals("1")) {
               playTreatmentToCall("1.au");
           } else if (s.equals("2")) {
           playTreatmentToCall("2.au");
           } else if (s.equals("3")) {
           playTreatmentToCall("3.au");
           } else if (s.equals("4")) {
           playTreatmentToCall("4.au");
           } else if (s.equals("5")) {
           playTreatmentToCall("5.au");
           } else if (s.equals("6")) {
           playTreatmentToCall("6.au");
           } else if (s.equals("7")) {
           playTreatmentToCall("7.au");
           } else if (s.equals("8")) {
           playTreatmentToCall("8.au");
           } else if (s.equals("9")) {
           playTreatmentToCall("9.au");
           } else if (s.equals("0")) {
           playTreatmentToCall("0.au");
           } else if (s.equals("*")) {
           playTreatmentToCall("star.au");
           } else if (s.equals("#")) {
           playTreatmentToCall("pound.au");
           }
       } catch (IOException e) {
           Logger.println("Unable to play dtmf treatment " + s);
       }
    }
    }

    public String getNumberOfCallsAsTreatment() {
        return getNumberOfCallsAsTreatment(getNumberOfCalls());
    }

    public String getNumberOfCallsAsTreatment(int n) {
        String s;

        if (n < 100) {
            if (n < 20 || (n < 100 && (n % 10) == 0)) {
                s = n + ".au";
            } else {
                int r = n % 10;

                s = (n - r) + ".au;" + r + ".au";
            }
        } else if (n < 1000) {
            int r = n % 100;
            int q = n / 100;

            if (r == 0) {
                s = (n / 100) + ".au";
            } else {
                s = q + ".au;hundred.au;" + getNumberOfCallsAsTreatment(r);
            }
        } else {
            s = "tts:" + n;
        }

        return s;
    }

    public ConferenceManager transferCall(String conferenceId)
        throws IOException {

    System.out.println("transferCall " + conferenceId);

    ConferenceManager conferenceManager = transferCall(this, conferenceId);

    String s = getNumberOfCallsAsTreatment(conferenceManager.getNumberOfMembers());

    playTreatmentToCall("you-are-caller-number.au;" + s);
    setMuted(false);

    playTreatmentToConference("joinCLICK.au");

    return conferenceManager;
    }

    public static ConferenceManager transferCall(String callId, String conferenceId) throws NoSuchElementException, IOException
    {
    CallHandler callHandler = CallHandler.findCall(callId);

    if (callHandler == null) {
        throw new NoSuchElementException("No such call:  " + callId);
    }

    if (callHandler instanceof IncomingCallHandler == false) {
        throw new NoSuchElementException("Only incoming calls can be transferred:  " + callId);
    }

    return ((IncomingCallHandler)callHandler).transferCall(conferenceId);
    }

    public static ConferenceManager transferCall(CallHandler callHandler, String conferenceId) throws IOException
    {
    /*
     * Get current conference manager and member.
     */
    ConferenceMember member = callHandler.getMember();

    ConferenceManager conferenceManager =
        callHandler.getConferenceManager();

        ConferenceManager newConferenceManager =
            ConferenceManager.getConference(conferenceId);

        if (newConferenceManager == null) {
            throw new NoSuchElementException("Can't create conference "
        + conferenceId);
        }

    CallParticipant cp = callHandler.getCallParticipant();

    //
    // XXX maybe we should have yet more commands to specify
        // the treatments for incoming calls.
    // Jon suggested a mode for setting up incoming call parameters
    // similar to how call setup is done for outgoing calls.
    //
    //cp.setConferenceJoinTreatment("joinCLICK.au");
    //cp.setConferenceLeaveTreatment("leaveCLICK.au");

    cp.setConferenceId(conferenceId);

    conferenceManager.transferMember(newConferenceManager, member);

    callHandler.setConferenceManager(newConferenceManager);

        try {
            newConferenceManager.addTreatment("joinCLICK.au");
        } catch (IOException e) {
            Logger.println("Call " + cp
                + " unable to play joinCLICK.au " + e.getMessage());
        }

    CallEvent event = new CallEvent(CallEvent.CALL_TRANSFERRED);

    event.setInfo("ConferenceReceiverPort='"
        + callHandler.getReceiveAddress().getPort() + "'"
        + " ConferencePayload='"
        +  newConferenceManager.getMediaInfo().getPayload() + "'"
        +  " BridgeIPAddress='"
        + Bridge.getPrivateHost() + "'");

    callHandler.sendCallEventNotification(event);

    Application.notifyConferenceMonitors(event); // now, we monitor direct conferences as we know conf

    return newConferenceManager;
    }

    public void playTreatmentToConference(String treatment) {
    if (newConferenceManager == null) {
        return;
    }

    try {
        newConferenceManager.addTreatment("joinCLICK.au");
    } catch (IOException e) {
        Logger.println("Call " + this
        + " unable to play treatment " + treatment
        + " " + e.getMessage());
    }
    }

    public TreatmentManager playTreatmentToCall(String treatment)
            throws IOException {

    treatmentManager = super.playTreatmentToCall(treatment);
    return treatmentManager;
    }

    public CallEventListener getRequestHandler() {
    return null;
    }

    public String toString() {
    return super.toString();
    }

}
