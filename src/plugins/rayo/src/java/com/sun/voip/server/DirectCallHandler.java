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
import java.text.ParseException;
import com.sun.voip.*;


public class DirectCallHandler extends Thread implements CallEventListener {
    private CallParticipant cp;
    private DirectCallEventListener listener;

    public DirectCallHandler(CallParticipant cp, DirectCallEventListener ln) {
    this.cp = cp;
        listener = ln;
    }

    /*
     * start a two party call
     */
    private DirectOutgoingCallHandler ch1, ch2;
    private int callState;
    public static final int INITIATED = 1;
    public static final int ESTABLISHED = 2;
    public static final int FAILED = 3;
    public static final int TERMINATED = 4;
    public static final int MIGRATED = 5;
    private Object stateLock = new Object();
    public void run() {
        setState(INITIATED);
        ch1 = new DirectOutgoingCallHandler(cp);
        ch1.sendInvite(null);
        String sdp = null;
        try{

             sdp = ch1.waitForOK();
        }
        catch(Exception ex){
            setState(FAILED);
            return;
        }

        CallParticipant cp2 = new CallParticipant();

    cp2.setCallAnswerTimeout(cp.getCallAnswerTimeout());
    cp2.setCallAnsweredTreatment(cp.getSecondPartyTreatment());
    cp2.setCallEndTreatment(cp.getSecondPartyCallEndTreatment());
    cp2.setCallId(cp.getSecondPartyCallId());
    cp2.setConferenceId(cp.getConferenceId());
    if (cp.getSecondPartyName() != null) {
        cp2.setName(cp.getSecondPartyName());
    } else {
        cp2.setName(cp.getSecondPartyNumber());
    }
    cp2.setDisplayName(cp.getName());
    if (cp2.getCallId() == null) {
        cp2.setCallId(CallHandler.getNewCallId());
    }
    cp2.setPhoneNumber(cp.getSecondPartyNumber());
    cp2.setVoiceDetection(cp.getSecondPartyVoiceDetection());

        ch2 = new DirectOutgoingCallHandler(cp2);
        ch1.setOtherCall(ch2);
        ch2.setOtherCall(ch1);
        ch2.sendInvite(sdp);
        try{
        sdp = ch2.waitForOK();
        ch1.sendAck(sdp);
        ch2.sendAck(null);
        setState(ESTABLISHED);
        ch1.waitForTerminate();
        ch2.waitForTerminate();
        setState(TERMINATED);
        }
        catch(Exception ex){
            setState(FAILED);
            if(ch1.getState() != ch1.TERMINATED){
                ch1.sendBye();
            }
            if(ch2.getState() != ch2.TERMINATED){
                ch2.sendBye();
            }
            return;
        }
    }

    public void setState(int state){
        synchronized(stateLock){
            callState = state;
            stateLock.notifyAll();
        }
        if(listener != null){
            switch(state){
                case INITIATED:
                    listener.initiated(cp.getCallOwner(), cp.getCallId());
                    break;
                case ESTABLISHED:
                    listener.established(cp.getCallOwner(), cp.getCallId());
                    break;
                case FAILED:
                    listener.failed(cp.getCallOwner(), cp.getCallId());
                    break;
                case TERMINATED:
                    listener.terminated(cp.getCallOwner(), cp.getCallId());
                    break;
            }
        }
    }

    public int stateChanged(){
        synchronized(stateLock){
            try {
                stateLock.wait();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            return callState;
        }
    }

    public void migrateToBridge(){
            terminate();
            TwoPartyCallHandler tch = new TwoPartyCallHandler(this, cp);
            tch.start();
            setState(MIGRATED);
    }

    public void terminate(){
        if(callState != MIGRATED){
            ch1.sendBye();
            ch2.sendBye();
        } else{
            try{
                ConferenceManager.endConference(cp.getConferenceId());
            } catch(ParseException e){
                Logger.println("Could not end conference");
            }
        }

    }

    public void callEventNotification(CallEvent event) {

    }

}
