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

import org.jivesoftware.openfire.XMPPServer;

import com.sun.voip.CallParticipant;
import com.sun.voip.CallState;
import com.sun.voip.MediaInfo;

import java.io.IOException;
import java.net.InetSocketAddress;

import java.util.*;

import org.dom4j.*;
import org.xmpp.packet.*;
import org.ifsoft.rayo.*;
import org.xmpp.jnodes.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SpeakerCallAgent extends CallSetupAgent
{
    private static final Logger Log = LoggerFactory.getLogger(SpeakerCallAgent.class);
    private CallParticipant cp;
    private MemberReceiver memberReceiver;
    private MemberSender memberSender;
    private MediaInfo mixerMediaPreference;

    public SpeakerCallAgent(CallHandler callHandler)
    {
        super(callHandler);

        cp = callHandler.getCallParticipant();
        mixerMediaPreference = callHandler.getConferenceManager().getMediaInfo();

        memberSender = callHandler.getMemberSender();
        memberReceiver = callHandler.getMemberReceiver();

        Log.info("SpeakerCallAgent init " + cp.getDisplayName() + " " + cp.getPhoneNumber() + " " + cp.isMuted());
    }

    public void initiateCall() throws IOException
    {
        String domainName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

        try {

            if ("CALL".equals(cp.getDisplayName()) && "Speaker".equals(cp.getProtocol()))	// bridge from call to speaker
            {
                memberSender.setChannel(new SpeakerChannel(cp.getOtherCall().getMemberReceiver()));
            }

            setState(CallState.ANSWERED);
            setEndpointAddress(null, (byte)0, (byte)0, (byte)0);
            setState(CallState.ESTABLISHED);

        } catch (Exception e) {

            Log.error("Call " + cp + ":  SpeakerCallAgent: initiateCall exception ");
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
}
