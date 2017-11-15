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

import com.sun.voip.CallEvent;
import com.sun.voip.CallEventListener;
import com.sun.voip.CallState;
import com.sun.voip.Logger;

import java.io.IOException;

import org.voicebridge.Config;

/**
 * Listen for incoming calls, play treatments to prompt the caller for
 * meeting id and pass code.  Transfer the call to the appropriate meeting.
 */
public class IncomingConferenceHandler extends Thread
    implements CallEventListener {

    private String meetingCode = "";
    private String passCode = "";

    private int state = WAITING_FOR_MEETING_CODE;

    private static final int WAITING_FOR_MEETING_CODE = 1;
    private static final int WAITING_FOR_PASS_CODE    = 2;
    private static final int IN_MEETING               = 3;

    private static final String ENTER_MEETING_CODE =
    "enter-conf-call-number.au;then-press-pound.au" ;

    private static final String INVALID_MEETING_CODE = "conf-invalid.au";

    private static final String INVALID_PASS_CODE = "bad_user_id_1.au"; // "badcode.au";

    private static final String INCOMING_TIMEOUT = "incoming_timeout.au";

    private static final String ENTER_REQUIRED_PASS_CODE =
    "please-enter-your.au;access-code.au;then-press-pound.au";

    private static final String LEAVE_MEETING = "leaveCLICK.au";

    private static final String CALL_MUTED = "conf-muted.au";

    private static final String CALL_UNMUTED = "conf-unmuted.au";

    private static final String CALLER_NUMBER = "you-are-caller-number.au";

    private static final int DEFAULT_TIMEOUT = 30000;
    private static int timeout = DEFAULT_TIMEOUT;

    private IncomingCallHandler incomingCallHandler;

    private String lastDtmfKey = "";
    private String phoneNo;

    /**
     * Constructor.
     */
    public IncomingConferenceHandler(IncomingCallHandler incomingCallHandler, String phoneNo) {
        this.incomingCallHandler = incomingCallHandler;
        this.phoneNo = phoneNo;

        incomingCallHandler.addCallEventListener(this);

        Logger.println("IncomingConferenceHandler:  " + phoneNo);
    }

    private String lastMessagePlayed;

    private void playTreatmentToCall(String treatment) {
        try {
        incomingCallHandler.playTreatmentToCall(treatment);
        } catch (IOException e) {
            Logger.println("Call " + incomingCallHandler
                + " Can't play treatment " + treatment);
        }
        lastMessagePlayed = treatment;
    }

    private void playConferenceId() {
    if (meetingCode == null || meetingCode.length() == 0) {
        return;
    }

    String s = "conference.au";

    for (int i = 0; i < meetingCode.length(); i++) {
        s += ";" + meetingCode.substring(i, i + 1) + ".au";
    }

    playTreatmentToCall(s);
    }

    private void playNumberOfCalls() {
    String s = incomingCallHandler.getNumberOfCallsAsTreatment();
    playTreatmentToCall(s + ";conf-peopleinconf.au");
    }

    /*
     * Called when status for an incoming call changes.
     */
    public void callEventNotification(CallEvent callEvent) {
    //if (Logger.logLevel >= Logger.LOG_INFO) {
        Logger.println("IncomingConferenceHandler " + callEvent.toString());
    //}

    if (callEvent.equals(CallEvent.STATE_CHANGED) &&
        callEvent.getCallState().equals(CallState.ESTABLISHED)) {

        /*
         * New incoming call
         */
        if (callEvent.getInfo() != null) {
            Logger.println("IncomingConferenceHandler:  " + callEvent.getInfo());
        }

        if (Config.getInstance().getMeetingCode(phoneNo) != null)
        {
            meetingCode = Config.getInstance().getMeetingCode(phoneNo);

            Logger.println("IncomingConferenceHandler:  meeting code " + meetingCode);

            if (Config.getInstance().getPassCode(meetingCode, phoneNo) == null)
            {
                try {
                    incomingCallHandler.transferCall(meetingCode);
                    state = IN_MEETING;

                } catch (IOException e) {
                    System.err.println("Exception joining meeting! " + meetingCode + " " + e.getMessage());
                }

            } else {
                state = WAITING_FOR_PASS_CODE;
                playTreatmentToCall(ENTER_REQUIRED_PASS_CODE);
                start();
            }

        } else {

            playTreatmentToCall(ENTER_MEETING_CODE);
            state = WAITING_FOR_MEETING_CODE;
            start();
        }

        return;
    }

    if (callEvent.equals(CallEvent.STATE_CHANGED) &&
        callEvent.getCallState().equals(CallState.ENDED)) {

        playTreatmentToCall(LEAVE_MEETING);
        return;
    }

    /*
     * We're only interested in dtmf keys
     */
        if (callEvent.equals(CallEvent.DTMF_KEY) == false) {
        return;
    }

        String dtmfKey = callEvent.getDtmfKey();

    if (state == WAITING_FOR_MEETING_CODE) {
        getMeetingCode(dtmfKey);
    } else if (state == WAITING_FOR_PASS_CODE) {
        getPassCode(dtmfKey);
    } else {
        if (lastDtmfKey.equals("*")) {
        if (dtmfKey.equals("1")) {
            incomingCallHandler.setMuted(true);
            playTreatmentToCall(CALL_MUTED);
        } else if (dtmfKey.equals("2")) {
            incomingCallHandler.setMuted(false);
            playTreatmentToCall(CALL_UNMUTED);
        } else if (dtmfKey.equals("9")) {
            playConferenceId();
        } else if (dtmfKey.equals("#")) {
            playNumberOfCalls();
        }
        }
    }

    lastDtmfKey = dtmfKey;
    }

    private void getMeetingCode(String dtmfKey) {
        if (!dtmfKey.equals("#")) {
            meetingCode += dtmfKey;  // accumulate meeting code
            return;
        }

        if (meetingCode.length() == 0) {
            playTreatmentToCall(INVALID_MEETING_CODE
            + ";" + ENTER_MEETING_CODE);
            return;
        }

        String confRoom = null;

        if (Config.getInstance().isValidConference(meetingCode))
        {
            confRoom = meetingCode;

        } else if (Config.getInstance().isValidConferenceExten(meetingCode))	{

            confRoom = Config.getInstance().getMeetingCode(meetingCode);

        }

        if (confRoom != null)
        {

            if (Config.getInstance().getPassCode(confRoom, phoneNo) == null)
            {
                try {
                    incomingCallHandler.transferCall(confRoom);
                    state = IN_MEETING;

                } catch (IOException e) {
                    System.err.println("Exception joining meeting! " + meetingCode + " " + e.getMessage());
                }

            } else {
                state = WAITING_FOR_PASS_CODE;
                playTreatmentToCall(ENTER_REQUIRED_PASS_CODE);
                return;
            }

        } else {
            playTreatmentToCall(INVALID_MEETING_CODE + ";" + ENTER_MEETING_CODE);
            meetingCode = "";
            state = WAITING_FOR_MEETING_CODE;
            return;
        }
    }

    private void getPassCode(String dtmfKey) {
        if (!dtmfKey.equals("#")) {
        passCode += dtmfKey;   // accumulate pass code
        return;
        }

        /*
         * For now, allow people to join without a passCode
         */
        int intPassCode = 0;

        if (passCode.length() > 0) {
            try {
                intPassCode = Integer.parseInt(passCode);

            } catch (NumberFormatException e) {
                    playTreatmentToCall(INVALID_PASS_CODE);

                passCode = "";
                return;
            }
        }

        if (Config.getInstance().isValidConferencePin(meetingCode, passCode))
        {
            try {
                incomingCallHandler.transferCall(meetingCode);
                state = IN_MEETING;

            } catch (IOException e) {
                System.err.println("Exception joining meeting! " + meetingCode + " " + e.getMessage());

                playTreatmentToCall(INVALID_PASS_CODE);
                passCode = "";
                state = WAITING_FOR_PASS_CODE;
            }

        } else {

            playTreatmentToCall(INVALID_PASS_CODE);
            passCode = "";
            state = WAITING_FOR_PASS_CODE;
        }
    }

    public void run() {
    /*
     * Timeout handler to re-prompt user
     */
    long startTime = System.currentTimeMillis();

    while (state == WAITING_FOR_MEETING_CODE ||
            state == WAITING_FOR_PASS_CODE) {

        int currentState = state;

        try {
        Thread.sleep(timeout);

        if (state != WAITING_FOR_MEETING_CODE &&
            state != WAITING_FOR_PASS_CODE) {

            break;
        }

        if (currentState == state) {
            if (System.currentTimeMillis() - startTime >=
                CallSetupAgent.getDefaultCallAnswerTimeout() * 1000) {

            playTreatmentToCall(INCOMING_TIMEOUT);

            /* We'd like to wait until the treatment is done
             * before cancelling the call.
             */
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }

                incomingCallHandler.cancelRequest(
                "Incoming call timeout");

            break;
            }

            playTreatmentToCall(lastMessagePlayed);
        }
        } catch (InterruptedException e) {
        Logger.println("Incoming ConferenceHandler Interrupted!");
        }
    }
    }

}
