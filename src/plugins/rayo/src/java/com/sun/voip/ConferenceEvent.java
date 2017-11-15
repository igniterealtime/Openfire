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

public class ConferenceEvent {

    public static final int CONFERENCE_STARTED = 1;
    public static final int CONFERENCE_ENDED = 2;

    public static final int MEMBER_JOINED = 3;
    public static final int MEMBER_LEFT = 4;

    private int event;
    private String conferenceId;

    private String callId;
    private InetSocketAddress memberAddress;
    private boolean isDistributedBridge = false;
    private int memberCount;

    public ConferenceEvent(int event, String conferenceId) {
    this.event = event;
    this.conferenceId = conferenceId;
    }

    public int getEvent() {
    return event;
    }

    public String getConferenceId() {
    return conferenceId;
    }

    public void setCallId(String callId) {
    this.callId = callId;
    }

    public String getCallId() {
    return callId;
    }

    public void setMemberCount(int memberCount) {
    this.memberCount = memberCount;
    }

    public int getMemberCount() {
    return memberCount;
    }

    public void setMemberAddress(InetSocketAddress memberAddress) {
    this.memberAddress = memberAddress;
    }

    public InetSocketAddress getMemberAddress() {
    return memberAddress;
    }

    public void setIsDistributedBridge(boolean isDistributedBridge) {
    this.isDistributedBridge = isDistributedBridge;
    }

    public boolean isDistributedBridge() {
    return isDistributedBridge;
    }

    public boolean equals(int event) {
    return this.event == event;
    }

    public String toString() {
    String s;

        switch (event) {
        case CONFERENCE_STARTED:
            s = "CONFERENCE_STARTED(" + event + ")"
            + ", conferenceId " + conferenceId;
        break;

        case CONFERENCE_ENDED:
            s = "CONFERENCE_ENDED(" + event + ")"
            + ", conferenceId " + conferenceId;
        break;

        case MEMBER_JOINED:
        s = "MEMBER_JOINED(" + event + ")"
            + ", conferenceId " + conferenceId
        + ", callId " + callId
        + ", count " + memberCount
        + ", memberAddress " + memberAddress;
        break;

        case MEMBER_LEFT:
            s = "MEMBER_LEFT(" + event + ")"
            + ", conferenceId " + conferenceId
        + ", callId " + callId
        + ", count " + memberCount
        + ", memberAddress " + memberAddress;
        break;

    default:
        s = "UNKNOWN ConferenceEevent(" + event + ")"
            + ", conferenceId " + conferenceId
        + ", callId " + callId
        + ", memberAddress " + memberAddress;
        break;
        }

    return s;
    }

}
