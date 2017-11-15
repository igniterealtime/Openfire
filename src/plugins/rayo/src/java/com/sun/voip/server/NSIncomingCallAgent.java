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
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.RtpPacket;
import com.sun.voip.SdpInfo;

import java.text.ParseException;
import java.net.InetSocketAddress;

/**
 * Non-Signaling handles calls setup by an external source.
 */
public class NSIncomingCallAgent extends CallSetupAgent {

    private SipUtil sipUtil;

    public NSIncomingCallAgent(CallHandler callHandler) {
        super(callHandler);

        MediaInfo mixerMediaPreference =
            callHandler.getConferenceManager().getMediaInfo();

        sipUtil = new SipUtil(mixerMediaPreference);
    }




    public void initiateCall() {
        setState(CallState.INVITED);

        CallParticipant cp = callHandler.getCallParticipant();

        String remoteMediaInfo = cp.getRemoteMediaInfo();

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + cp
            + ":   NSIncomingCallAgent remoteMediaInfo " + remoteMediaInfo);
        }

        String[] tokens = remoteMediaInfo.split("\\+");

        /*
         * The remote media info is the SDP info but instead of
         * new line as the separator, it has "+".
         * Reformat the SDP with \r\n.
         */
        String sdp = "";

        for (int i = 0; i < tokens.length; i++) {
            sdp += tokens[i] + "\r\n";
        }

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Call " + cp
            + ":  NSIncomingCallAgent Sdp\n" + sdp);
        }

        SdpInfo sdpInfo = null;

        try {
            sdpInfo = sipUtil.getSdpInfo(sdp, true);

                String remoteHost = sdpInfo.getRemoteHost();
                int remotePort = sdpInfo.getRemotePort();

                Logger.println("Call " + cp
                    + ":  NSIncomingCallAgent:  remote socket " + remoteHost + " "
                    + remotePort + " mediaInfo " + sdpInfo.getMediaInfo());

                    InetSocketAddress isa =
                        new InetSocketAddress(remoteHost, remotePort);

                    setEndpointAddress(isa, sdpInfo.getMediaInfo().getPayload(),
                    sdpInfo.getTransmitMediaInfo().getPayload(),
                    sdpInfo.getTelephoneEventPayload());

        } catch (ParseException e) {
            Logger.println("Call " + cp
            + ":  NSIncomingCallAgent couldn't parse sdp");
                cancelRequest("NSIncomingCallAgent couldn't parse sdp");
            return;
        }

        setState(CallState.ANSWERED);
            setState(CallState.ESTABLISHED);
    }

}
