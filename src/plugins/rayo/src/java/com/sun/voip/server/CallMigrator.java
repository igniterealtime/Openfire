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
import com.sun.voip.CallState;
import com.sun.voip.Logger;
import com.sun.voip.CallEventListener;

public class CallMigrator extends Thread {
    CallEventListener requestHandler;
    CallParticipant cp;

    public CallMigrator(CallEventListener requestHandler, CallParticipant cp) {
	this.requestHandler = requestHandler;
	this.cp = cp;
    }

    /*
     * Migrate a call.  Set up the new call, join the conference and
     * terminate the original call.
     */
    public void run() {
	String callId = cp.getCallId();  // call to migrate

	CallHandler callHandler = CallHandler.findMigratingCall(callId);

	if (callHandler != null) {
	    /*
	     * Call migration is in progess.
	     * Cancel previous migration request.
	     */
	     callHandler.cancelRequest("new migration requested");
	}

	CallHandler previousCall = CallHandler.findCall(callId);

	if (previousCall == null) {
	    if (cp.getConferenceId() == null) {
	        Logger.println("Call migrator can't find call Id " + callId);
		return;
	    }

	    /*
	     * Treat it like a new call
	     */
	    migrateWithNoPreviousCall(requestHandler, cp);
	    return;
	}

if (false) {
	if (previousCall.isCallEstablished() == false) {
	    Logger.println(
		"Call migrator can't migrate call which is not established");

	    return;
	}
}

	CallParticipant previousCp = previousCall.getCallParticipant();

	String previousEndTreatment = previousCp.getCallEndTreatment();
        String previousLeaveTreatment = previousCp.getConferenceLeaveTreatment();

	previousCp.setCallEndTreatment(null);
	previousCp.setConferenceLeaveTreatment(null);

	cp.setMuted(previousCp.isMuted());	// preserve mute

	Logger.println("Call migrating " + previousCp + " preserving mute "
	    + previousCp.isMuted());

	if (cp.getConferenceId() == null) {
	    cp.setConferenceId(previousCp.getConferenceId());
	}

	if (cp.getName() == null) {
	    cp.setName(previousCp.getName());
	}

	/*
	 * SecondPartyNumber is the new number to call.
	 * If it starts with "Id-", it's a callId of
	 * a call already in progress.  Otherwise, it's a phone number to call.
	 */
	String secondParty = cp.getSecondPartyNumber();

	if (secondParty.indexOf("Id-") == 0) {
	    callHandler = CallHandler.findCall(secondParty.substring(3));

	    if (callHandler == null) {
		Logger.println("Can't find existing call to " + secondParty);
		//Logger.logLevel = logLevel;
		return;
	    }

	    cp = callHandler.getCallParticipant();
	    cp.setCallId(previousCp.getCallId());
	} else {
	    /*
	     * Invite the secondParty if call is not already setup.
	     * After the party answers terminate the previous call.
	     */
	    cp.setPhoneNumber(cp.getSecondPartyNumber());
	    cp.setConferenceJoinTreatment(null);

	    /*
	     * Use the request handler from the previous call (if there
	     * is one) so that status changes will be sent to the same socket
	     * as the previous call.  This will make the migration seemless.
	     */
	    CallEventListener previousCallEventListener = (CallEventListener) previousCall.getRequestHandler();

	    if (previousCallEventListener != null) {
		requestHandler = previousCallEventListener;
	    }

	    /*
	     * Previous call is migrating.  It will probably be
	     * common for someone to hangup their cell phone before
	     * answering the new call.  In this case we need to
	     * suppress status from the previous call.
	     * However, if the previous call ends and then migration fails,
	     * we need to send call ending status back.
	     */
	    previousCall.suppressStatus = true;

	    OutgoingCallHandler newCall =
		new OutgoingCallHandler(requestHandler, cp);

	    previousCall.getMember().migrating();

	    synchronized(this) {
	        newCall.suppressStatus = true;

	        newCall.start();		// call new party

	        /*
	         * Wait for call to be established
	         */
	        if (newCall.waitForCallToBeEstablished() == false) {
		    String reason = newCall.getReasonCallEnded();

		    Logger.println("Migration failed: " + reason);

		    previousCp.setConferenceLeaveTreatment(previousLeaveTreatment);
		    previousCp.setCallEndTreatment(previousEndTreatment);
		    CallEvent callEvent = new CallEvent(CallEvent.STATE_CHANGED);

		    callEvent.setCallState(new CallState(CallState.ENDED));

		    callEvent.setInfo("Migration failed: " + reason);

		    newCall.suppressStatus = false;
		    newCall.sendCallEventNotification(callEvent);

		    previousCall.suppressStatus = false;

		    if (!previousCall.isCallEstablished()) {
	 	        previousCall.sendCallEventNotification(callEvent);
		    }

		    //Logger.logLevel = logLevel;
	            return;
	        }

	        newCall.suppressStatus = false;
	    }

	    if (previousCall.isCallEstablished() == true) {
		Logger.println("migrate mix descriptors for " + previousCall);
	        newCall.getMember().migrate(previousCall.getMember());
	    } else {
		Logger.println("migrate:  previous call is not established "
		    + previousCall);
	    }
	}

	Logger.println("Call " + previousCp
	    + " migrated to " + cp.getPhoneNumber());

	previousCall.suppressStatus = false;
	cp.setMigrateCall(false);	// call is no longer migrating

	/*
	 * If the previous call ended early, we need to re-add
	 * it as a call status listener so that it will get the MIGRATED status
	 */
	if (!previousCall.isCallEstablished()) {
	    previousCall.addCallEventListener(previousCall.getRequestHandler());
	}

	CallEvent callEvent = new CallEvent(CallEvent.MIGRATED);

	callEvent.setInfo(previousCp + " migrated to " + cp);

	previousCall.sendCallEventNotification(callEvent);

	previousCall.suppressStatus = true;

	/*
	 * Suppress conference leave treatment.  No other calls should
	 * know that this call migrated.
	 */
	previousCp.setCallEndTreatment(null);
	previousCp.setConferenceLeaveTreatment(null);

	previousCall.cancelRequest("Call " + previousCp
	    + " migrated to " + cp.getPhoneNumber());
    }

    private void migrateWithNoPreviousCall(CallEventListener requestHandler,
	    CallParticipant cp) {

        cp.setPhoneNumber(cp.getSecondPartyNumber());

	OutgoingCallHandler callHandler = new OutgoingCallHandler(requestHandler, cp);

	synchronized(this) {
	    callHandler.start();		// call new party

	    /*
	     * Wait for call to be established
	     */
	    if (callHandler.waitForCallToBeEstablished() == false) {
	        return;
	    }
	}

	cp.setMigrateCall(false);	// call is no longer migrating

	CallEvent callEvent = new CallEvent(CallEvent.MIGRATED);

	callEvent.setInfo("migrated to " + cp);

	callHandler.sendCallEventNotification(callEvent);
    }

    /*
     * Cancel a migration request
     * This simply cancels the new call before it's been answered.
     */
    public static void hangup(String callId, String reason) {
	/*
	 * Find the new call being migrated to and cancel that call.
	 */
	CallHandler callHandler = CallHandler.findMigratingCall(callId);

	if (callHandler == null) {
	    Logger.println("Can't find migrating call for " + callId);
	    return;
        }

	callHandler.cancelRequest(reason);
    }

}
