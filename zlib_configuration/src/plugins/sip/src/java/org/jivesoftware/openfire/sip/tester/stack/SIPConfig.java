/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.sip.tester.stack;

/**
 * Title: SIPark
 *
 * @author Thiago Rocha Camargo (thiago@jivesoftware.com)
 */

public class SIPConfig {

    private static boolean askPassword = true;

    private static String audioPort = "20002";

    private static String authenticationRealm = "";

    private static String authUserName = "";

    private static int bindRetries = 3;

    private static int defaultBufferLength = 200;

    private static String defaultDomain = "";

    private static String displayName = "";

    private static String excessiveURIChar = "( )-";

    private static boolean failCallInUserMismatch = false;

    private static boolean firstLaunch = false;

    private static String httpProxy = "";

    private static String IPAddress = "";

    private static String javaHome = "";

    private static int keepAliveDelay = 30;

    private static int localPort = 5060;

    private static boolean loopAlerts = true;

    private static String mediaSource = null;

    private static String os = "windows";

    private static String outboundProxy = "";

    private static boolean preferIPv4Stack = true;

    private static String preferredAudioCodec = "3";

    private static String preferredNetworkAddress = "";

    private static String preferredNetworkInterface = "";

    private static String preferredVideoCodec = "29";

    private static String publicAddress = "";

    private static String registrarAddress = "";

    private static int registrarPort = 5060;

    private static String registrarTransport = "UDP";

    private static int registrationExpiration = 2000;

    private static String retransmissionFilter = "";

    private static String routerPath = "org.jivesoftware.openfire.sip.tester.stack.SipCommRouter";

    private static String stackName = "SIPark";

    private static String stackPath = "gov.nist";

    private static String transport = "";

    private static String userName = "";

    private static String videoPort = "20006";

    private static int waitUnregistration = 1000;

    private static String stunServer = "";

    private static String stunPort = "";

    private static boolean useStun = false;

    public static String getAudioPort() {
        return SIPConfig.audioPort;
    }

    public static String getAuthenticationRealm() {
        return SIPConfig.authenticationRealm;
    }

    public static String getAuthUserName() {
        return SIPConfig.authUserName;
    }

    public static int getBindRetries() {
        return SIPConfig.bindRetries;
    }

    public static int getDefaultBufferLength() {
        return SIPConfig.defaultBufferLength;
    }

    public static String getDefaultDomain() {
        return SIPConfig.defaultDomain;
    }

    public static String getDisplayName() {
        return SIPConfig.displayName;
    }

    public static String getExcessiveURIChar() {
        return SIPConfig.excessiveURIChar;
    }

    public static String getHttpProxy() {
        return SIPConfig.httpProxy;
    }

    public static String getIPAddress() {
        return SIPConfig.IPAddress;
    }

    public static String getJavaHome() {
        return SIPConfig.javaHome;
    }

    public static int getKeepAliveDelay() {
        return SIPConfig.keepAliveDelay;
    }

    public static int getLocalPort() {
        return SIPConfig.localPort;
    }

    public static String getMediaSource() {
        return SIPConfig.mediaSource;
    }

    public static String getOs() {
        return SIPConfig.os;
    }

    public static String getOutboundProxy() {
        return SIPConfig.outboundProxy;
    }

    public static String getPreferredAudioCodec() {
        return SIPConfig.preferredAudioCodec;
    }

    public static String getPreferredNetworkAddress() {
        return SIPConfig.preferredNetworkAddress;
    }

    public static String getPreferredNetworkInterface() {
        return SIPConfig.preferredNetworkInterface;
    }

    public static String getPreferredVideoCodec() {
        return SIPConfig.preferredVideoCodec;
    }

    public static String getPublicAddress() {
        return SIPConfig.publicAddress;
    }

    public static String getRegistrarAddress() {
        return SIPConfig.registrarAddress;
    }

    public static int getRegistrarPort() {
        return SIPConfig.registrarPort;
    }

    public static String getRegistrarTransport() {
        return SIPConfig.registrarTransport;
    }

    public static int getRegistrationExpiration() {
        return SIPConfig.registrationExpiration;
    }

    public static String getRetransmissionFilter() {
        return SIPConfig.retransmissionFilter;
    }

    public static String getRouterPath() {
        return SIPConfig.routerPath;
    }

    public static String getStackName() {
        return SIPConfig.stackName;
    }

    public static String getStackPath() {
        return SIPConfig.stackPath;
    }

    public static String getTransport() {
        return SIPConfig.transport;
    }

    public static String getUserName() {
        return SIPConfig.userName;
    }

    public static String getVideoPort() {
        return SIPConfig.videoPort;
    }

    public static int getWaitUnregistration() {
        return SIPConfig.waitUnregistration;
    }

    public static boolean isAskPassword() {
        return SIPConfig.askPassword;
    }

    public static boolean isFailCallInUserMismatch() {
        return SIPConfig.failCallInUserMismatch;
    }

    public static boolean isFirstLaunch() {
        return SIPConfig.firstLaunch;
    }

    public static boolean isLoopAlerts() {
        return SIPConfig.loopAlerts;
    }

    public static boolean isPreferIPv4Stack() {
        return SIPConfig.preferIPv4Stack;
    }

    protected static void setAskPassword(boolean askPassword) {
        SIPConfig.askPassword = askPassword;
    }

    protected static void setAudioPort(String audioPort) {
        SIPConfig.audioPort = audioPort;
    }

    protected static void setAuthenticationRealm(String authenticationRealm) {
        SIPConfig.authenticationRealm = authenticationRealm;
    }

    protected static void setAuthUserName(String authUserName) {
        SIPConfig.authUserName = authUserName;
    }

    protected static void setBindRetries(int bindRetries) {
        SIPConfig.bindRetries = bindRetries;
    }

    protected static void setDefaultBufferLength(int defaultBufferLength) {
        SIPConfig.defaultBufferLength = defaultBufferLength;
    }

    protected static void setDefaultDomain(String defaultDomain) {
        SIPConfig.defaultDomain = defaultDomain;
    }

    protected static void setDisplayName(String displayName) {
        SIPConfig.displayName = displayName;
    }

    protected static void setExcessiveURIChar(String excessiveURIChar) {
        SIPConfig.excessiveURIChar = excessiveURIChar;
    }

    protected static void setFailCallInUserMismatch(
            boolean failCallInUserMismatch) {
        SIPConfig.failCallInUserMismatch = failCallInUserMismatch;
    }

    protected static void setFirstLaunch(boolean firstLaunch) {
        SIPConfig.firstLaunch = firstLaunch;
    }

    protected static void setHttpProxy(String httpProxy) {
        SIPConfig.httpProxy = httpProxy;
    }

    protected static void setIPAddress(String address) {
        SIPConfig.IPAddress = address;
    }

    protected static void setJavaHome(String javaHome) {
        SIPConfig.javaHome = javaHome;
    }

    protected static void setKeepAliveDelay(int keepAliveDelay) {
        SIPConfig.keepAliveDelay = keepAliveDelay;
    }

    protected static void setLocalPort(int localPort) {
        SIPConfig.localPort = localPort;
    }

    protected static void setLoopAlerts(boolean loopAlerts) {
        SIPConfig.loopAlerts = loopAlerts;
    }

    protected static void setMediaSource(String mediaSource) {
        SIPConfig.mediaSource = mediaSource;
    }

    protected static void setOs(String os) {
        SIPConfig.os = os;
    }

    protected static void setOutboundProxy(String outboundProxy) {
        SIPConfig.outboundProxy = outboundProxy;
    }

    protected static void setPreferIPv4Stack(boolean preferIPv4Stack) {
        SIPConfig.preferIPv4Stack = preferIPv4Stack;
    }

    protected static void setPreferredAudioCodec(String preferredAudioCodec) {
        SIPConfig.preferredAudioCodec = preferredAudioCodec;
    }

    public static void setPreferredNetworkAddress(
            String preferredNetworkAddress) {
        SIPConfig.preferredNetworkAddress = preferredNetworkAddress;
    }

    protected static void setPreferredNetworkInterface(
            String preferredNetworkInterface) {
        SIPConfig.preferredNetworkInterface = preferredNetworkInterface;
    }

    protected static void setPreferredVideoCodec(String preferredVideoCodec) {
        SIPConfig.preferredVideoCodec = preferredVideoCodec;
    }

    protected static void setPublicAddress(String publicAddress) {
        SIPConfig.publicAddress = publicAddress;
    }

    protected static void setRegistrarAddress(String registrarAddress) {
        SIPConfig.registrarAddress = registrarAddress;
    }

    protected static void setRegistrarPort(int registrarPort) {
        SIPConfig.registrarPort = registrarPort;
    }

    protected static void setRegistrarTransport(String registrarTransport) {
        SIPConfig.registrarTransport = registrarTransport;
    }

    protected static void setRegistrationExpiration(int registrationExpiration) {
        SIPConfig.registrationExpiration = registrationExpiration;
    }

    protected static void setRetransmissionFilter(String retransmissionFilter) {
        SIPConfig.retransmissionFilter = retransmissionFilter;
    }

    protected static void setRouterPath(String routerPath) {
        SIPConfig.routerPath = routerPath;
    }

    public static void setServer(String server) {
        SIPConfig.defaultDomain = server;
        SIPConfig.authenticationRealm = server;
        SIPConfig.registrarAddress = server;
        SIPConfig.outboundProxy = server + ":" + SIPConfig.registrarPort + "/" + SIPConfig.registrarTransport;
        SIPConfig.setSystemProperties();
    }

    protected static void setStackName(String stackName) {
        SIPConfig.stackName = stackName;
    }

    protected static void setStackPath(String stackPath) {
        SIPConfig.stackPath = stackPath;
    }

    public static void setSystemProperties() {

        // javax.sip System Properties
        System.setProperty("javax.sip.IP_ADDRESS", SIPConfig.IPAddress);
        System.setProperty("javax.sip.STACK_NAME", SIPConfig.stackName);
        System.setProperty("javax.sip.ROUTER_PATH", SIPConfig.routerPath);
        System.setProperty("javax.sip.OUTBOUND_PROXY", SIPConfig.outboundProxy);
        System.setProperty("javax.sip.RETRANSMISSON_FILTER", "");
        System.setProperty("javax.sip.EXTENSION_METHODS", "");
        System.setProperty("javax.sip.RETRANSMISSION_FILTER", "true");
        System.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");

    }

    protected static void setTransport(String transport) {
        SIPConfig.transport = transport;
    }

    public static void setUserName(String userName) {
        SIPConfig.userName = userName;
    }

    protected static void setVideoPort(String videoPort) {
        SIPConfig.videoPort = videoPort;
    }

    protected static void setWaitUnregistration(int waitUnregistration) {
        SIPConfig.waitUnregistration = waitUnregistration;
    }

    public static String getStunPort() {
        return SIPConfig.stunPort;
    }

    public static void setStunPort(String stunPort) {
        SIPConfig.stunPort = stunPort;
    }

    public static String getStunServer() {
        return SIPConfig.stunServer;
    }

    public static void setStunServer(String stunServer) {
        SIPConfig.stunServer = stunServer;
    }

    public static boolean isUseStun() {
        return SIPConfig.useStun;
    }

    public static void setUseStun(boolean useStun) {
        SIPConfig.useStun = useStun;
    }

}
