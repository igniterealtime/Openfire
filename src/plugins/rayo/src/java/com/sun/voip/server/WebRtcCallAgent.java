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

import java.io.IOException;

public class WebRtcCallAgent extends CallSetupAgent
{
    private CallParticipant cp;
    private MemberReceiver memberReceiver;
    private MemberSender memberSender;
    private MediaInfo mixerMediaPreference;

    public WebRtcCallAgent(CallHandler callHandler)
    {

        super(callHandler);

        cp = callHandler.getCallParticipant();

        mixerMediaPreference = callHandler.getConferenceManager().getMediaInfo();

        memberSender = callHandler.getMemberSender();
        memberSender.setChannel(cp.getChannel());
        memberReceiver = callHandler.getMemberReceiver();

        callHandler.setEndpointAddress(null, (byte) (cp.getMediaPreference() == "PCM/48000/2" ? 111 : 0), (byte)0, (byte)0);

    }

    public void initiateCall() throws IOException
    {
        try {
            setState(CallState.ESTABLISHED);

        } catch (Exception e) {

            Logger.println("Call " + cp + ":  WebRtcCallAgent: initiateCall exception " + e);
            e.printStackTrace();
        }
    }

    public String getSdp()
    {
        return null;
    }

    public void setRemoteMediaInfo(String sdp)
    {
        return;
    }

    public void terminateCall()
    {

    }

}
