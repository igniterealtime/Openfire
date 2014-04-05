/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jitsi.videobridge.openfire;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.*;

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



public class Config extends HttpServlet
{
    private static final Logger Log = LoggerFactory.getLogger(Config.class);
	public static final long serialVersionUID = 24362462L;

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		try {
			Log.info("Config servlet");
			String hostname = XMPPServer.getInstance().getServerInfo().getHostname();
			String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
			boolean websockets = XMPPServer.getInstance().getPluginManager().getPlugin("websockets") != null;

			writeHeader(response);

			ServletOutputStream out = response.getOutputStream();

			String iceServers = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.iceservers", "");
			String resolution = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.resolution", "720");
			String useNicks = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.usenicks", "false");
			String useIPv6 = JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.useipv6", "false");
			String recordVideo = JiveGlobals.getProperty("org.jitsi.videobridge.media.record", "true");

			out.println("var config = {");
			out.println("    hosts: {");
			out.println("        domain: '" + domain + "',");
			out.println("        muc: 'conference." + domain + "',");
			out.println("        bridge: 'jitsi-videobridge." + domain + "',");
			out.println("    },");
			if (!iceServers.trim().equals("")) out.println("    iceServers: " + iceServers + ",");
			out.println("    useIPv6: " + useIPv6 + ",");
			out.println("    useNicks: " + useNicks + ",");
			out.println("    recordVideo: " + recordVideo + ",");
			out.println("    useWebsockets: " + (websockets ? "true" : "false") + ",");
			out.println("    resolution: '" + resolution + "',");
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
