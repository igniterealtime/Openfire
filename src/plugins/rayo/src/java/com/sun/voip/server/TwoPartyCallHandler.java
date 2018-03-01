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
import com.sun.voip.Logger;
import com.sun.voip.CallEventListener;

public class TwoPartyCallHandler extends Thread {
    private CallEventListener callEventListener;
    private CallParticipant cp;

    public TwoPartyCallHandler(CallEventListener csl, CallParticipant cp) {
    this.callEventListener = csl;
    this.cp = cp;
    }

    /*
     * start a two party call
     */
    public void run() {
    OutgoingCallHandler callHandler1 =
        new OutgoingCallHandler(callEventListener, cp);

    synchronized(this) {
        callHandler1.start();		// call first party

        /*
         * Wait for first party to answer.
         */
        if (callHandler1.waitForCallToBeAnswered() == false) {
            return;
        }
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

    OutgoingCallHandler callHandler2 =
        new OutgoingCallHandler(callEventListener, cp2);

    synchronized(this) {
        /*
         * Each call has to know about the other so that when
         * one hangs up, the other call is terminated.
         */
        callHandler1.setOtherCall(callHandler2);
        callHandler2.setOtherCall(callHandler1);

        callHandler2.start();		// call second party

        if (callHandler2.waitForCallToBeAnswered() == true) {
            /*
             * Second party answered, stop treatment to first party.
             */
            callHandler1.stopCallAnsweredTreatment();
        callHandler1.stopCallEstablishedTreatment();
        }
    }
    }

}
