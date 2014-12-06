/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jitsi.videobridge.openfire;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.vcard.VCardManager;

import org.slf4j.*;
import org.slf4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.security.Principal;

import org.dom4j.*;


public class Config extends HttpServlet
{
    private static final Logger Log = LoggerFactory.getLogger(Config.class);
	public static final long serialVersionUID = 24362462L;

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		try {
			Log.info("Config servlet");
			String hostname = XMPPServer.getInstance().getServerInfo().getHostname();
			String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
			String userName = "null";
			String userAvatar = "null";

			String securityEnabled = JiveGlobals.getProperty("ofmeet.security.enabled", "true");

			if ("true".equals(securityEnabled))
			{
				userName = request.getUserPrincipal().getName();
				VCardManager vcardManager = VCardManager.getInstance();
				Element vcard = vcardManager.getVCard(userName);

				if (vcard != null)
				{
					Element photo = vcard.element("PHOTO");

					if (photo != null)
					{
						String type = photo.element("TYPE").getText();
						String binval = photo.element("BINVAL").getText();
						userAvatar = "data:" + type + ";base64," + binval.replace("\n", "").replace("\r", "");;
					}
				}
			}

			boolean nodejs = XMPPServer.getInstance().getPluginManager().getPlugin("nodejs") != null;

			writeHeader(response);

			ServletOutputStream out = response.getOutputStream();

			String iceServers 		= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.iceservers", "");
			String resolution 		= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.resolution", "360");
			String audioMixer		= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.audio.mixer", "false");
			String audioBandwidth 	= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.audio.bandwidth", "64");
			String videoBandwidth 	= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.video.bandwidth", "512");
			String useNicks 		= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.usenicks", "false");
			String useIPv6 			= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.useipv6", "false");
			String useStunTurn 		= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.use.stunturn", "false");
			String recordVideo 		= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.media.record", "false");
			String defaultSipNumber = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.default.sip.number", "");
			String adaptiveLastN 	= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.adaptive.lastn", "false");
			String adaptiveSimulcast= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.adaptive.simulcast", "false");
			String useRtcpMux 		= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.use.rtcp.mux", "true");
			String useBundle 		= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.use.bundle", "false");
			String enableWelcomePage= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.enable.welcomePage", "false");
			String enableSimulcast 	= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.enable.simulcast", "false");
			String enableRtpStats 	= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.enable.rtp.stats", "true");
			String openSctp 		= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.open.sctp", "true");
			String desktopSharing 	= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.desktop.sharing", "ext");
			String chromeExtensionId= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.chrome.extension.id", "fohfnhgabmicpkjcpjpjongpijcffaba");
			String channelLastN 	= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.channel.lastn", "-1");
			String desktopShareSrcs	= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.desktop.sharing.sources", "[\"screen\", \"window\"]");
			String minChromeExtVer	= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.min.chrome.ext.ver", "0.1");

			out.println("var config = {");
			out.println("    hosts: {");
			out.println("        domain: '" + domain + "',");
			out.println("        muc: 'conference." + domain + "',");
			out.println("        bridge: 'ofmeet-jitsi-videobridge." + domain + "',");
			out.println("        call_control: 'ofmeet-call-control." + domain + "',");
			out.println("    },");
			out.println("    getroomnode: function (path)");
			out.println("    {");
			out.println("		var name = 'r';");
			out.println("		var roomnode = null;");

			out.println("		var results = new RegExp('[\\?&]' + name + '=([^&#]*)').exec(window.location.href);");

			out.println("		if (!results)");
			out.println("			roomnode = null; ");
			out.println("		else 	roomnode = results[1] || undefined;	");

			out.println("		if (!roomnode) {");
			out.println("			roomnode = Math.random().toString(36).substr(2, 20);");
			out.println("			window.history.pushState('VideoChat', 'Room: ' + roomnode, path + '?r=' + roomnode);");
			out.println("		}");
			out.println("		return roomnode;    ");
			out.println("    },	");
			if (!iceServers.trim().equals("")) out.println("    iceServers: " + iceServers + ",");
			out.println("    useStunTurn: " + useStunTurn + ",");
			out.println("    useIPv6: " + useIPv6 + ",");
			out.println("    useNicks: " + useNicks + ",");
			out.println("    adaptiveLastN: " + adaptiveLastN + ",");
			out.println("    adaptiveSimulcast: " + adaptiveSimulcast + ",");
			out.println("    useRtcpMux: " + useRtcpMux + ",");
			out.println("    useBundle: " + useBundle + ",");
			out.println("    enableWelcomePage: " + enableWelcomePage + ",");
			out.println("    enableSimulcast: " + enableSimulcast + ",");
			out.println("    enableRtpStats: " + enableRtpStats + ",");
			out.println("    openSctp: " + openSctp + ",");
			out.println("    enableRecording: " + recordVideo + ",");
			out.println("    clientNode: 'http://igniterealtime.org/ofmeet',");
			out.println("    defaultSipNumber: '" + defaultSipNumber + "',");
			out.println("    desktopSharing: '" + desktopSharing + "',");
			out.println("    chromeExtensionId: '" + chromeExtensionId + "',");
			out.println("    desktopSharingSources: '" + desktopShareSrcs + "',");
			out.println("    minChromeExtVersion: '" + minChromeExtVer + "',");
			out.println("    channelLastN: " + channelLastN + ",");
			out.println("    useNodeJs: " + (nodejs ? "true" : "false") + ",");
			out.println("    resolution: '" + resolution + "',");
			out.println("    audioMixer: " + audioMixer + ",");
			out.println("    audioBandwidth: '" + audioBandwidth + "',");
			out.println("    videoBandwidth: '" + videoBandwidth + "',");
			out.println("    userName: '" + userName + "',");
			out.println("    userAvatar: '" + userAvatar + "',");
			out.println("    disablePrezi: true,");
			out.println("    bosh: window.location.protocol + '//' + window.location.host + '/http-bind/'");
			out.println("};	");

		}
		catch(Exception e) {
			Log.info("Config doGet Error", e);
		}
	}


    private void writeHeader(HttpServletResponse response)
    {

		try {
			response.setHeader("Expires", "Sat, 6 May 1995 12:00:00 GMT");
			response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
			response.addHeader("Cache-Control", "post-check=0, pre-check=0");
			response.setHeader("Pragma", "no-cache");
			response.setHeader("Content-Type", "application/javascript");
			response.setHeader("Connection", "close");
        }
        catch(Exception e)
        {
			Log.info("Config writeHeader Error", e);
        }
	}
}
