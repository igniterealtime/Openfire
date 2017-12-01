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
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;

import java.io.IOException;
import java.net.InetSocketAddress;

import java.util.*;

import org.dom4j.*;
import org.xmpp.packet.*;
import org.ifsoft.rayo.*;

public class VideobridgeCallAgent extends CallSetupAgent
{
    private CallParticipant cp;
    private MemberReceiver memberReceiver;
    private MemberSender memberSender;
    private MediaInfo mixerMediaPreference;

    private static int startRTPPort = 0;
    private static int stopRTPPort = 0;
    private static Integer nextRTPPort = 0;

    public VideobridgeCallAgent(CallHandler callHandler)
    {
        super(callHandler);

        cp = callHandler.getCallParticipant();
        mixerMediaPreference = callHandler.getConferenceManager().getMediaInfo();
        memberSender = callHandler.getMemberSender();
        memberReceiver = callHandler.getMemberReceiver();

        String s = System.getProperty("com.sun.voip.server.FIRST_VIDEOBRIDGE_RTP_PORT");

        try {
            startRTPPort = Integer.parseInt(s);

        } catch (NumberFormatException e) {

            startRTPPort = 60000;
        }

        s = System.getProperty("com.sun.voip.server.LAST_VIDEOBRIDGE_RTP_PORT");

        try {
            stopRTPPort = Integer.parseInt(s);

        } catch (NumberFormatException e) {

            stopRTPPort = 70000;
        }

        nextRTPPort = startRTPPort;
    }

    public void initiateCall() throws IOException
    {
        String domainName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

        try {
            InetSocketAddress isaLocal = callHandler.getReceiveAddress();

            int localRTPPort = isaLocal.getPort();
            int localRTCPPort = localRTPPort + 1;
            int remoteRTPPort = nextRTPPort;
            int remoteRTCPPort = remoteRTPPort + 1;

            synchronized (nextRTPPort)
            {
                nextRTPPort++;

                if (nextRTPPort > stopRTPPort) nextRTPPort = startRTPPort;
            }
            setState(CallState.INVITED);


            IQ iq = new IQ(IQ.Type.set);
            iq.setFrom(cp.getCallOwner());
            iq.setTo(domainName);

            String id = "rayo-" + System.currentTimeMillis();

            Element colibri = iq.setChildElement("colibri", "urn:xmpp:rayo:colibri:1");
            colibri.addAttribute("videobridge", cp.getConferenceId());
            colibri.addAttribute("localrtpport",String.valueOf(remoteRTPPort));
            colibri.addAttribute("localrtcpport",String.valueOf(remoteRTCPPort));
            colibri.addAttribute("remotertpport",String.valueOf(localRTPPort));
            colibri.addAttribute("remotertcpport",String.valueOf(localRTCPPort));
            colibri.addAttribute("codec", cp.getMediaPreference().equals("PCM/48000/2") ? "opus" : "pcmu");
            RayoPlugin.component.sendPacket(iq);
            setState(CallState.ANSWERED);

            InetSocketAddress isaRemote = new InetSocketAddress("localhost", remoteRTPPort);
            setEndpointAddress(isaRemote, (byte) (cp.getMediaPreference().equals("PCM/48000/2") ? 111 : 0), (byte)0, (byte)0);
            setState(CallState.ESTABLISHED);

        } catch (Exception e) {

            Logger.println("Call " + cp + ":  VideobridgeCallAgent: initiateCall exception ");
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
