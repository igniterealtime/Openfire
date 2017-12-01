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

package com.sun.voip;

import java.net.InetSocketAddress;

public class CallEvent {

    public static final int NEW_CONNECTION        = 0;
    public static final int STATE_CHANGED	  = 1;
    public static final int NO_SUCH_WHISPER_GROUP = 2;
    public static final int JOIN_TIMEOUT          = 3;
    public static final int CALL_ANSWER_TIMEOUT   = 4;
    public static final int NUMBER_OF_CALLS       = 5;
    public static final int TREATMENT_DONE	  = 6;
    public static final int STARTED_SPEAKING      = 7;
    public static final int STOPPED_SPEAKING      = 8;
    public static final int DTMF_KEY              = 9;
    public static final int MIGRATED	          = 10;
    public static final int MIGRATION_FAILED      = 11;
    public static final int CALL_TRANSFERRED	  = 12;
    public static final int BUSY_HERE	          = 13;
    public static final int CANT_START_CONFERENCE = 14;
    public static final int CANT_CREATE_MEMBER    = 15;
    public static final int H323_NOT_IMPLEMENTED  = 16;
    public static final int INFO                  = 17;

    private static final int LAST_EVENT = 17;

    private static String[] eventString = {
        "000 New Connection",
        "001 STATE CHANGED",
        "050 NO SUCH WHISPER GROUP",
        "120 JOIN CONFIRMATION TIMEOUT",
        "127 CALL ANSWER TIMEOUT",
        "220 NUMBER OF CALLS",
        "230 TREATMENT DONE",
        "250 STARTED SPEAKING",
        "259 STOPPED SPEAKING",
        "269 DTMF",
        "270 MIGRATED",
        "275 MIGRATION FAILED no answer",
        "279 CALL TRANSFERRED",
        "486 Busy Here",
        "900 Can't start conference",
        "910 Can't create member",
        "920 H323 is not implemented",
    ""
    };

    private int event;
    private String info;
    private String callId;
    private String conferenceId;
    private String callInfo;
    private CallState callState;
    private boolean startedSpeaking;
    private boolean stoppedSpeaking;
    private String dtmfKey;
    private String treatmentId;
    private int numberOfCalls;

    public CallEvent(int event) {
    this.event = event;
    info = "";
    }

    public CallEvent(String s) {
    for (int i = 0; i < LAST_EVENT; i++) {
        int ix;

        if ((ix = s.indexOf(eventString[i])) < 0) {
        continue;
        }

        event = i;

        s = s.substring(ix + eventString[i].length() + 1);

        info = getInfo(s);

        if ((callId = getValue(s, "CallId=")) == null) {
        break;
        }

        if ((conferenceId = getValue(s, "ConferenceId=")) == null) {
        break;
        }

        if ((callInfo = getValue(s, "CallInfo=")) == null) {
        break;
        }

        return;
    }

    event = INFO;
    info = s;
    }

    private String getInfo(String s) {
    int ix = s.indexOf("CallId=");

    if (ix <= 0) {
        return "";
    }

    return s.substring(0, ix);
    }

    private String getValue(String s, String key) {
        int ix;

    if ((ix = s.indexOf(key)) < 0) {
        if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("Missing " + key + ":  " + s);
        }
        return null;
    }

    if (ix > 0) {
           info = s.substring(0, ix);
    }

        if ((ix = s.indexOf("'")) < 0) {
            Logger.println("Missing quote:  " + s);
            return null;
        }

        s = s.substring(ix + 1);

        if ((ix = s.indexOf("'")) < 0) {
            Logger.println("Missing quote:  " + s);
            return null;
    }

    return s.substring(0, ix);
    }

    public int getEvent() {
    return event;
    }

    public void setInfo(String info) {
    this.info = info;

    if (this.info == null) {
        this.info = "";
    }
    }

    public String getInfo() {
    return info;
    }

    public void setCallId(String callId) {
    this.callId = callId;
    }

    public String getCallId() {
    return callId;
    }

    public void setConferenceId(String conferenceId) {
    this.conferenceId = conferenceId;
    }

    public String getConferenceId() {
    return conferenceId;
    }

    public void setCallInfo(String callInfo) {
    this.callInfo = callInfo;
    }

    public String getCallInfo() {
    return callInfo;
    }

    public void setCallState(CallState callState) {
        this.callState = callState;
    }

    public CallState getCallState() {
        return callState;
    }

    public void setStartedSpeaking() {
    startedSpeaking = true;
    }

    public boolean startedSpeaking() {
    return startedSpeaking;
    }

    public void setStoppedSpeaking() {
    stoppedSpeaking = true;
    }

    public boolean stoppedSpeaking() {
    return stoppedSpeaking;
    }

    public void setDtmfKey(String dtmfKey) {
    this.dtmfKey = dtmfKey;
    }

    public String getDtmfKey() {
    return dtmfKey;
    }

    public void setTreatmentId(String treatmentId) {
    this.treatmentId = treatmentId;
    }

    public String getTreatmentId() {
    return treatmentId;
    }

    public void setNumberOfCalls(int numberOfCalls) {
    this.numberOfCalls = numberOfCalls;
    }

    public int getNumberOfCalls() {
    return numberOfCalls;
    }

    public boolean equals(int event) {
    return this.event == event;
    }

    public static String getEventString(int event) {
    if (event < 0 || event > LAST_EVENT) {
        return "Invalid Event:  " + event;
    }

    return eventString[event];
    }

    public String toString() {
    if (event < 0 || event > LAST_EVENT) {
        return "Invalid Event:  " + event;
    }

    String s = eventString[event];

    if (event == STATE_CHANGED) {
        s = callState.toString();
    }

    if (info != null && info.length() > 0) {
        s += " " + info;
    }

    if (event == DTMF_KEY) {
        s += " DTMFKey='" + dtmfKey + "'";
    } else if (event == TREATMENT_DONE) {
        s += " Treatment='" + treatmentId + "'";
    } else if (event == NUMBER_OF_CALLS) {
        s += " NumberOfCalls='" + numberOfCalls + "'";
    }

    if (callId != null && callId.length() > 0) {
        s += " CallId='" + callId + "'";
    }

    if (conferenceId != null && conferenceId.length() > 0) {
            s += " ConferenceId='" + conferenceId +"'";
    }

    if (callInfo != null && callInfo.length() > 0) {
            s += " CallInfo='" + callInfo + "'";
    }

    return BridgeVersion.getVersion() + " " + s;
    }

}
