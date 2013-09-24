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

import com.sun.voip.Logger;
import java.io.IOException;
import java.io.InterruptedIOException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Bridge {

    private static String privateHost;
    private static int privateControlPort = 6666;
    private static int privateSipPort = 5060;

    private static String publicHost = "127.0.0.1";
    private static int publicControlPort = 6666;
    private static int publicSipPort = 5060;

    private static String bridgeLocation = "OPL";
    private static char fileSep = System.getProperty("file.separator").charAt(0);

	private static final Bridge instance = new Bridge();

    private static boolean localhostSecurity = false;

    private static String defaultProtocol = "SIP";

	public static Bridge getInstance() {
		return instance;
	}

    public static String getVersion() {
		return "0.0.0.1";
    }

    public static String getBridgeLocation() {
	return bridgeLocation;
    }

    public static void setBridgeLocation(String bridgeLocation) {
	Bridge.bridgeLocation = bridgeLocation;
    }

    public static String getBridgeLogDirectory() {
	return System.getProperty("user.dir") + fileSep + ".." + fileSep + "logs" + fileSep;
    }

    public static String getPrivateHost() {
	return privateHost;
    }

    public static String getPublicHost() {
	return publicHost;
    }

    public static void setPrivateHost(String privateHost) {
	Bridge.privateHost = privateHost;
    }

    public static void setPublicHost(String publicHost) {
	Bridge.publicHost = publicHost;
    }

    public static int getPrivateControlPort() {
	return privateControlPort;
    }

    public static int getPrivateSipPort() {
	return privateSipPort;
    }


    public static int getPublicControlPort() {
	return publicControlPort;
    }

    public static int getPublicSipPort() {
	return publicSipPort;
    }

    public static void setLocalhostSecurity(boolean localhostSecurity) {
	Bridge.localhostSecurity = localhostSecurity;
    }

    public static boolean getLocalhostSecurity() {
	return localhostSecurity;
    }

    public static void setDefaultProtocol(String defaultProtocol) {
	Bridge.defaultProtocol = defaultProtocol;
    }

    public static String getDefaultProtocol() {
	return defaultProtocol;
    }

    public static InetSocketAddress getLocalBridgeAddress() {
	return (InetSocketAddress) new InetSocketAddress(privateHost,  privateControlPort);
    }

}
