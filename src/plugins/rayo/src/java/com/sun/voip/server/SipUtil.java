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
import com.sun.voip.Logger;
import com.sun.voip.SdpManager;
import com.sun.voip.MediaInfo;
import com.sun.voip.SdpInfo;
import com.sun.voip.RtpPacket;

import javax.sip.*;
import javax.sip.header.*;
import javax.sip.message.*;
import javax.sip.address.*;

import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.Vector;

import java.text.ParseException;

import org.voicebridge.*;

/**
 * A utility class used to construct and parse sip
 * messages, and other SIP-transaction-related tasks.
 */
public class SipUtil {
    /* static variables */
    private static HeaderFactory headerFactory;
    private static AddressFactory addressFactory;
    private static MessageFactory messageFactory;
    private static SipProvider sipProvider;
    private static String ourIpAddress;
    private static int ourSipPort;

    private static String ourPublicIpAddress;
    private static int ourPublicSipPort;

    private static Vector supportedMedia = new Vector();

    private static boolean initialized = false;

    private SdpManager sdpManager;

    public SipUtil() {
        this(null);
    }

    public SipUtil(MediaInfo mediaInfo)
    {
        if (!initialized) {
            initialize();
        }

        sdpManager = new SdpManager();

        if (mediaInfo == null)
        {
            try {
                mediaInfo = sdpManager.findMediaInfo(RtpPacket.PCMU_ENCODING, 8000, 1);

                Logger.println("SipUtil:  Preference default media " + mediaInfo);

            } catch (ParseException e) {
                Logger.println("SipUtil:  Invalid media info, can't set preference" + e.getMessage());
            }
        }

        sdpManager.setPreferredMediaInfo(mediaInfo);
    }

    /**
     * Static initializer.
     */
    public static void initialize() {
        headerFactory = SipServer.getHeaderFactory();
        addressFactory = SipServer.getAddressFactory();
        messageFactory = SipServer.getMessageFactory();
        sipProvider = SipServer.getSipProvider();

    ourIpAddress = SipServer.getSipStack().getIPAddress();
    ourSipPort = sipProvider.getListeningPoint().getPort();

    ourPublicIpAddress = ourIpAddress;

    String s = System.getProperty("com.sun.voip.server.PUBLIC_IP_ADDRESS");

    if (s != null && s.length() > 0) {
        try {
            ourPublicIpAddress = InetAddress.getByName(s).getHostAddress();
        } catch (UnknownHostException e) {
        Logger.println("Invalid public IP address, using " + ourIpAddress);
        }
    }

    Logger.println("Bridge public address:    " + ourPublicIpAddress);

    ourPublicSipPort = ourSipPort;

    s = System.getProperty("com.sun.voip.server.PUBLIC_SIP_PORT");

    if (s != null) {
        try {
        ourPublicSipPort = Integer.parseInt(s);
        } catch (NumberFormatException e) {
        Logger.println("Invalid public SIP Port, using " + ourSipPort);
        }
    }

    Logger.println("Bridge public SIP port:   " + ourSipPort);

    supportedMedia.add(new MediaInfo(
            (byte)0, RtpPacket.PCMU_ENCODING, 8000, 1, false));

       // supportedMedia.add(new MediaInfo(
       //     (byte)101, RtpPacket.PCM_ENCODING, 8000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)102, RtpPacket.PCM_ENCODING, 8000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)103, RtpPacket.PCM_ENCODING, 16000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)104, RtpPacket.PCM_ENCODING, 16000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)105, RtpPacket.PCM_ENCODING, 32000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)106, RtpPacket.PCM_ENCODING, 32000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)107, RtpPacket.PCM_ENCODING, 44100, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)108, RtpPacket.PCM_ENCODING, 44100, 2, false));

if (false) {
        supportedMedia.add(new MediaInfo(
            (byte)109, RtpPacket.PCM_ENCODING, 48000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)110, RtpPacket.PCM_ENCODING, 48000, 2, false));
}

        supportedMedia.add(new MediaInfo(
            (byte)111, RtpPacket.PCM_ENCODING, 48000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)112, RtpPacket.PCMU_ENCODING, 16000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)113, RtpPacket.PCMU_ENCODING, 16000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)114, RtpPacket.PCMU_ENCODING, 32000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)115, RtpPacket.PCMU_ENCODING, 32000, 2, false));

if (false) {
        supportedMedia.add(new MediaInfo(
            (byte)116, RtpPacket.PCMU_ENCODING, 44100, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)117, RtpPacket.PCMU_ENCODING, 44100, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)118, RtpPacket.PCMU_ENCODING, 48000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)119, RtpPacket.PCMU_ENCODING, 48000, 2, false));
}

        supportedMedia.add(new MediaInfo(
            (byte)120, RtpPacket.SPEEX_ENCODING, 8000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)121, RtpPacket.SPEEX_ENCODING, 8000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)122, RtpPacket.SPEEX_ENCODING, 16000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)123, RtpPacket.SPEEX_ENCODING, 16000, 2, false));

        supportedMedia.add(new MediaInfo(
            (byte)124, RtpPacket.SPEEX_ENCODING, 32000, 1, false));

        supportedMedia.add(new MediaInfo(
            (byte)125, RtpPacket.SPEEX_ENCODING, 32000, 2, false));

    SdpManager.setSupportedMedia(supportedMedia);

    initialized = true;
    }

    public ClientTransaction sendInvite(CallParticipant cp, InetSocketAddress isa)
        throws InvalidArgumentException, SipException, ParseException {

        if (Bridge.getPublicHost().equals(isa.getAddress()) == false) {
            isa = new InetSocketAddress(Bridge.getPublicHost(), isa.getPort());
        }

        String sdp = generateSdp(cp, isa);
        return sendInvite(cp, sdp);
    }

    public String generateSdp(CallParticipant cp, InetSocketAddress isa) {
        String sdp = sdpManager.generateSdp(cp, "MeetingCentral", isa);

    String s = "a=conferenceId:" + cp.getConferenceId();

    if (cp.getMediaPreference() != null) {
        s += ":" + cp.getMediaPreference();

        if (cp.getConferenceDisplayName() != null) {
        s += ":" + cp.getConferenceDisplayName();
        }
    }

        sdp += s + "\r\n";

    if (cp.getRemoteCallId() != null) {
        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Setting callId in sdp to "
            + cp.getRemoteCallId());
        }
        sdp += "a=callId:" + cp.getRemoteCallId() + "\r\n";
    }

    if (cp.isDistributedBridge()) {
        sdp += "a=userName:DistributedBridge\r\n";
        sdp += "a=distributedBridge\r\n";
    }

    sdp += "a=transmitMediaInfoOk\r\n";

    return sdp;
    }

    /**
     * Builds and sends a standard INVITE message, with a
     * CSeq number 1.
     * @param cp the CallParticipant
     * @param tpccName String identifying who is making the call.
     * @return transaction Id of newly created transaction.
     * @throws ParseException if message cannot be parsed
     * @throws SipException if general Sip Exception occurs.
     */
    public ClientTransaction sendInvite(CallParticipant cp, String sdp)
        throws ParseException, InvalidArgumentException, SipException {

        // variables used for message building
        SipURI fromAddress = null;
        Address fromNameAddress = null;
        FromHeader fromHeader = null;
    SipURI toAddress = null;
        Address toNameAddress = null;
        ToHeader toHeader = null;
        SipURI requestURI = null;
        CallIdHeader callIdHeader = null;
        CSeqHeader cSeqHeader = null;
    AllowEventsHeader allowEventsHeader = null;
        ViaHeader viaHeader = null;
        ArrayList viaHeaders = null;
        ContentTypeHeader contentTypeHeader = null;
        Request invite = null;
        String obProxy = null;

        /*
         * We need a name and number to identify the party placing the call.
         * There are some restrictions when using the Vocal Proxy.
         *
         * If the number being called has 4 digits, then the request is
     * going to the proxy.  The vocal proxy requires the <tpccName>
     * in the From: header to be provisioned.
         * We use 4099 which we provisioned.
         *
         * We generate a header like this:
         *
         *    From: "<display name>" <sip:<tpccName>@129.148.75.131:5060>
         *    To: "20315" <sip:20315@129.148.75.22:5060>
         *
         * 20315 is the phone being called.
     *
         * "<display name>" is the string that will show up as the callerID
         * on phone 20315.
         *
         * <tpccName> is the identifier of who's making the call.
         */
    String fromName = cp.getDisplayName();
    String fromNumber = cp.getDisplayName(); //cp.getFromPhoneNumber();
    String toNumber = cp.getPhoneNumber();
    String transport = "udp";

Logger.println("XXX from = " + fromName + " " + cp);

        // int toSipPort = SipServer.getSipAddress().getPort();
    // XXX this should be the proxy or gateway port!
    int toSipPort = 5060;

    String proxy = cp.getSipProxy();

    if (proxy == null) {
        proxy = SipServer.getDefaultSipProxy();
    }

    String voipGateway = null;

    if (toNumber.indexOf("sip:") == 0) {
        /*
         * If a SIP URI is specified, parse it and send
         * the request directly to the target unless sendSipUriToProxy is false.
         *
         * If this request is sent to the proxy
         * the endpoint must be registered with the proxy.
         */
        Address address = null;
        SipURI sipURI = null;
        String host = null;
        String user = null;

        try {
            address = addressFactory.createAddress(toNumber);
            sipURI = (SipURI)address.getURI();
            host = sipURI.getHost();
            user = sipURI.getUser();
        } catch (ParseException e) {
            Logger.println("parse exception:  " + toNumber + " sipUri " + sipURI + " host " + host + " user " + user);
        }

        if (Logger.logLevel >= Logger.LOG_SIP) {
            Logger.println("address: " + address);
            Logger.println("sipURI: " + sipURI);
            Logger.println("host: " + host);
            Logger.println("user: " + user);
        }

        if (SipServer.getSendSipUriToProxy() == false && user != null) {
            InetAddress inetAddress;

            try {
                inetAddress = InetAddress.getByName(host);

                voipGateway = host; //inetAddress.getHostAddress();

                int port = sipURI.getPort();

                if (port > 0) {
                    toSipPort = port;
                }

                toNumber = user;

                /*
                 * Keep just the User information from the URI.
                 * XXX Not sure why I should do this.
                 */
                //cp.setPhoneNumber(toNumber);

                Logger.println("Call " + cp + " Sending INVITE directly to " + inetAddress + ":" + toSipPort);
            } catch (UnknownHostException e) {

                /*
                 * Let proxy handle it
                 */
                voipGateway = proxy;

                Logger.println("Call " + cp + " Using proxy " + proxy + " for " + toNumber);

                // XXX Not sure why I should do this.
                //cp.setPhoneNumber(toNumber.substring(4));  // skip sip:
                toNumber = toNumber.substring(4);
            }

        } else {
            voipGateway = proxy;

            Logger.println("Call " + cp + " Using proxy " + proxy  + " for " + toNumber);

            // XXX Not sure why I should do this.
            //cp.setPhoneNumber(toNumber.substring(4));  // skip sip:
            toNumber = toNumber.substring(4);
        }

    } else {		// telephone number

        transport =  System.getProperty("com.sun.voip.server.PROTOCOL");

        if (toNumber.indexOf("tel:") == 0)
        {
            toNumber = toNumber.substring(4);
        }

        voipGateway = proxy;
        Logger.println("Call " + cp + " Using proxy " + proxy + " for " + toNumber);
    }

    if (toNumber.indexOf("@") < 0 && CallHandler.enablePSTNCalls() == false) {
        throw new SipException("PSTN calls are not allowed:  " + cp);
    }

    ArrayList<ProxyCredentials> proxyCredentialList = SipServer.getProxyCredentials();
    boolean gatewayRequired = false;

    if (voipGateway == null)
    {
        if (proxy == null)
        {
            if (proxyCredentialList.size() == 0)
            {
                Logger.println("Call " + cp + " no voipGateway is available!");
                throw new SipException("No voip Gateway! " + cp);

            } else gatewayRequired = true;


        } else {

            voipGateway = proxy;
            gatewayRequired = true;
        }

    } else {

        if (voipGateway.equals(proxy))
            gatewayRequired = true;
    }

    if (gatewayRequired)
    {
        Logger.println("XXXX gatewayRequired");

        if (proxyCredentialList.size() != 0)
        {
            Logger.println("XXXX gatewayRequired 1");
            int voipIndex = 0;

            for (int i=0; i<proxyCredentialList.size(); i++)
            {
                ProxyCredentials proxyCredentials = proxyCredentialList.get(i);

                if (voipGateway.equals(proxyCredentials.getName()))
                {
                    voipIndex = i;
                }
            }

            ProxyCredentials proxyCredentials = proxyCredentialList.get(voipIndex);

            //fromName = proxyCredentials.getUserDisplay();
            voipGateway = proxyCredentials.getHost();
            obProxy = proxyCredentials.getProxy();
            //fromAddress = addressFactory.createSipURI(proxyCredentials.getUserName(), voipGateway);
            fromAddress = addressFactory.createSipURI(fromName, voipGateway);

            //cp.setProxyCredentials(proxyCredentials);				// we need this to match SIP transaction later
            //cp.setDisplayName(proxyCredentials.getUserDisplay());	// we need this to get proxy authentication details later
        } else {
            Logger.println("XXXX gatewayRequired 2");
        }

        Logger.println("XXXX gatewayRequired 3");

        toAddress = addressFactory.createSipURI(toNumber, voipGateway);

    } else {

        Logger.println("XXXX gatewayRequired 4");

        Logger.println("fromNumber " + fromNumber);

        if (fromNumber.startsWith("sip:"))
            fromAddress = (SipURI)addressFactory.createAddress(fromNumber).getURI();
        else
            fromAddress = addressFactory.createSipURI(fromNumber, ourIpAddress);

        fromAddress.setPort(ourSipPort);
        toAddress = addressFactory.createSipURI(toNumber, voipGateway);
    }

    Logger.println("XXXX gatewayRequired 5");

    Logger.println("from " + fromAddress);
    Logger.println("to " + toAddress);

    fromNameAddress = addressFactory.createAddress(fromName, fromAddress);
    fromHeader = headerFactory.createFromHeader(fromNameAddress, new Integer((int)(Math.random() * 10000)).toString());

    /* create To Header
     * e.g. "Willie Walker"<sip:30039@152.70.1.28:5060>
     *   where "Willie Walker" == cp.getName()
     *                      30039 == cp.getNumber()
     *             152.70.1.28 == cp.getIpAddress()
     *                    5060 == cp.getPort()
     */

    Logger.println("XXXX gatewayRequired 6");

    if (Bridge.getPrivateHost().startsWith("127.") &&
        voipGateway.equals("127.0.0.1") == false) {

        throw new SipException("The bridge's ip address is "
        + Bridge.getPrivateHost()
        + ". It is not possible to initiate a call to " + voipGateway);
    }

    if (Bridge.getPrivateHost().startsWith("127.") == false &&
        voipGateway.startsWith("127.") == true) {

        throw new SipException("The bridge's ip address must be "
        + Bridge.getPrivateHost()
        + " in order to issue a call to " + voipGateway);
    }

    Logger.writeFile("Call " + cp + " voip gateway / proxy " + voipGateway
        + " port " + toSipPort);

    /*
     * Don't do this because port should be that of the toNumber if specified
     * otherwise that of the voipGateway
     */
        // toAddress.setPort(toSipPort);

        toNameAddress = addressFactory.createAddress(toNumber, toAddress);
        toHeader = headerFactory.createToHeader(toNameAddress, null);

        /* create request URI (the first line of the request)
         * e.g. INVITE sip:61202@129.145.176.239:5060;transport=udp SIP/2.0
         *   where                    INVITE == (specfied later)
         *                      (requestURI) == (same as toAddress)
         *             transport=udp SIP/2.0 == (transport)
         */
    int ix = toNumber.indexOf("@");

    if (ix >= 0) {
        toNumber = toNumber.substring(0, ix);
    }

    Logger.println("XXXX gatewayRequired 7");

        requestURI = addressFactory.createSipURI(toNumber, voipGateway);

    requestURI.setPort(toSipPort);

        requestURI.setTransportParam
            (sipProvider.getListeningPoint(transport).getTransport());

        /* create Via headers
         * e.g. Via: SIP/2.0/UDP 152.70.1.43:5060;branch=z9hG4bK5
         *   where      SIP/2.0/UDP == (transport)
         *         152.70.1.43:5060 == (local address and SIP port)
         *          branch=z9hG4bk5 == (auto generated branch id)
         */
        viaHeader = headerFactory.createViaHeader(ourIpAddress,  ourSipPort, sipProvider.getListeningPoint(transport).getTransport(), null);
        //viaHeader.setBranch(MessageFactoryImpl.generateBranchId());
        viaHeaders = new ArrayList();
        viaHeaders.add(viaHeader);

        /* create CallId header
         * e.g. Call-Id: 7727452ebac7ff5ac0c65baa7250e2f5@152.70.1.43
         *   where 77274.... == (globally unique identifier)
         */
        callIdHeader = sipProvider.getNewCallId();

        /* create Seq header
         * e.g. CSeq: 1 INVITE
         *   where      1 == (CSeq number - motonically increasing within
         *                    each SIP callId)
         *         INVITE == (request method)
         */
        cSeqHeader = headerFactory.createCSeqHeader(1, Request.INVITE);

        // Create a new MaxForwardsHeader
        MaxForwardsHeader maxForwards =
            headerFactory.createMaxForwardsHeader(70);

        // create INVITE message.  Put everything together
    invite = messageFactory.createRequest(requestURI,
            Request.INVITE, callIdHeader, cSeqHeader,
            fromHeader, toHeader, viaHeaders, maxForwards);

    Logger.println("XXXX gatewayRequired 10");

    if (SdpManager.useTelephoneEvent() == true) {
        allowEventsHeader =
            headerFactory.createAllowEventsHeader("telephone-event");

        invite.addHeader(allowEventsHeader);
    }

        /* Contact Header (where subsequent requests should be sent to)
         * e.g. Contact: "Awarenex" <sip:Awarenex@152.70.1.43:5060>;
         *   where   "Awarenex" <sip:... == (local Address)
         */
        SipURI contactURI = null;

        if (fromNumber.startsWith("sip:"))
            contactURI = (SipURI)addressFactory.createAddress(fromNumber).getURI();
        else
            contactURI = addressFactory.createSipURI(fromNumber, ourPublicIpAddress);


    Logger.println("XXXX gatewayRequired 12");

    contactURI.setPort(ourPublicSipPort);

        Address contactAddress =
            addressFactory.createAddress(contactURI);

    contactAddress.setDisplayName(fromName);

        ContactHeader contactHeader =
        headerFactory.createContactHeader(contactAddress);

    invite.addHeader(contactHeader);

    Logger.println("XXXX gatewayRequired 14");

    if (obProxy != null)
    {
        try {
            SipURI routeURI = (SipURI) addressFactory.createURI("sip:" + obProxy + ";lr");
            RouteHeader routeHeader = headerFactory.createRouteHeader(addressFactory.createAddress(routeURI));
            invite.addHeader(routeHeader);

        } catch (Exception e) {

            Logger.error("Creating registration route error " + e);
        }
    }

    Logger.println("XXXX gatewayRequired 16");

    if (cp.isAutoAnswer())
    {
        Logger.println("Call " + cp + " alert-info added");

        try {

            SIPAlertInfo alertInfo = new SIPAlertInfo();
            alertInfo.setNamePair("info=alert-autoanswer") ;
            invite.addHeader(alertInfo) ;

        } catch (Exception e) {

            Logger.error("Creating alert info error " + e);
        }
    }


    Logger.println("XXXX gatewayRequired 18");

    if (sdp != null) {
            contentTypeHeader =
        headerFactory.createContentTypeHeader("application", "sdp");

            invite.setContent(sdp, contentTypeHeader);
        }

    // Create the client transaction.
    ClientTransaction clientTransaction;

    Logger.writeFile("Invite\n" + invite);

    try {
        clientTransaction =
            sipProvider.getNewClientTransaction(invite);
    } catch (Exception e) {
        Logger.println("Call " + cp + " sendInvite failed:  " + e.getMessage());
        return null;
    }

    Logger.println("XXXX gatewayRequired 19");
        clientTransaction.sendRequest();
    Logger.println("XXXX gatewayRequired 20");
    return clientTransaction;
    }

    public static boolean isSipUri(String phoneNumber) {
    if (phoneNumber == null || phoneNumber.indexOf("sip:") != 0) {
        return false;
    }

        Address address = null;
        SipURI sipURI = null;
        String host = null;
        String user = null;

        try {
            address = addressFactory.createAddress(phoneNumber);
            sipURI = (SipURI)address.getURI();
            host = sipURI.getHost();
            user = sipURI.getUser();
        } catch (ParseException e) {
        return false;
        }

        return user != null;
    }

    /**
     * builds and sends a standard ACK message with the TPC
     * server's address and port in the sdp.
     *
     * @param clientTransaction ClientTransaction for this call
     * @param sdpBody String sdp body for this call
     * @param isa InetSocketAddress of conference receiver
     * @throws ParsException if message cannot be parsed
     * @throws SipException if general sip exception occurs.
     * @throws TransactionDoesNotExistException if transaction can
     *         not be found.
     */
    public void sendAckWithTPCAddress(ClientTransaction clientTransaction, String sdpBody, InetSocketAddress isa)
            throws TransactionDoesNotExistException, ParseException,
            SipException {

        int start = sdpBody.indexOf("c=IN IP4 ");
        int finish = sdpBody.indexOf("\r", start);

        if (Logger.logLevel >= Logger.LOG_SIP) {
            Logger.println("modifying sdp with IP " +
            isa.getAddress() + " port " + isa.getPort());
        }

        String newSdp = sdpBody.substring(0, start+9) + isa.getAddress().getHostAddress() + sdpBody.substring(finish);

        start = newSdp.indexOf("m=audio ");

        if (start > - 1) {
            newSdp = newSdp.substring(0, start + 8) + isa.getPort() + newSdp.substring(newSdp.indexOf(" RTP/AVP"));

            //only return PCMU 8000
            start = newSdp.indexOf("RTP/AVP ");
            String pcmu = "RTP/AVP 0 13 101\r\n" + "a=rtpmap:0 PCMU/8000\r\n";
            newSdp = newSdp.substring(0, start) + pcmu ;
        }

        if (Logger.logLevel >= Logger.LOG_SIP) {
            Logger.println("sdp 1\n" + newSdp);
        }

        /**
         * Build and send a standard ACK message, based on the sdpBody
         * given and the previous transaction of the call participant.
        */

        Dialog dialog = clientTransaction.getDialog();

        Request ackRequest = dialog.createRequest(Request.ACK);

        ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");

        ackRequest.setContent(newSdp, contentTypeHeader);

        Logger.writeFile(ackRequest.toString());
        dialog.sendAck(ackRequest);
        return;
    }

    /**
     * builds and sends a standard ACK message with no SDP.
     *
     * @param clientTransaction ClientTransaction for this call
     * @throws ParsException if message cannot be parsed
     * @throws SipException if general sip exception occurs.
     * @throws TransactionDoesNotExistException if transaction can
     *         not be found.
     */
    public void sendAck(ClientTransaction clientTransaction)
            throws TransactionDoesNotExistException, ParseException,
            SipException {

        /**
         * Build and send a standard ACK message
     */
    Dialog dialog = clientTransaction.getDialog();

    //Request ackRequest = dialog.createRequest(Request.ACK);
    Request ackRequest = clientTransaction.createAck();

        dialog.sendAck(ackRequest);
    return;
    }

    /**
     * builds and sends a CANCEL message starting with an message.
     * @param clientTransaction most recent client transaction for call
     * @return transaction id of the request
     * @throws ParsEexception if message cannot be parsed
     * @throws SipException if general sip exception occurs.
     * @throws TransactionDoesNotExistException if transaction can
     *         not be found.
     */
    public void sendCancel(ClientTransaction clientTransaction)
            throws TransactionDoesNotExistException, ParseException,
            SipException {

    Request cancel = clientTransaction.createCancel();

    clientTransaction = sipProvider.getNewClientTransaction(cancel);

    clientTransaction.sendRequest();
    }

    public void sendCancel(ServerTransaction st)
            throws TransactionDoesNotExistException, ParseException,
            SipException {

        Dialog dialog = st.getDialog();
        dialog.incrementLocalSequenceNumber();
        Request cancelRequest = dialog.createRequest(Request.CANCEL);

        dialog.sendRequest(sipProvider.getNewClientTransaction(cancelRequest));
    }

    /**
     * builds and sends a standard BYE message.
     * @param clientTransaction Most recent client transaction for call
     * @throws ParseException if message cannot be parsed
     * @throws SipException if general sip exception occurs.
     * @throws TransactionDoesNotExistException if transaction can
     *         not be found.
     */
    public void sendBye(ClientTransaction clientTransaction)
            throws TransactionDoesNotExistException, ParseException,
            SipException, InvalidArgumentException {

    Dialog dialog = clientTransaction.getDialog();

    /*
     * Sip stack takes care of this.
     */
    //dialog.incrementLocalSequenceNumber();

        Request byeRequest = dialog.createRequest(Request.BYE);

        dialog.sendRequest(sipProvider.getNewClientTransaction(byeRequest));
    }

    /**
     * builds and sends a standard BYE message.
     * @param serverTransaction
     */
    public void sendBye(ServerTransaction st)
            throws TransactionDoesNotExistException, ParseException,
            SipException, InvalidArgumentException {

        Dialog dialog = st.getDialog();

    /*
     * Sip stack takes care of this.
     */
        //dialog.incrementLocalSequenceNumber();

        Request byeRequest = dialog.createRequest(Request.BYE);

    Logger.writeFile(byeRequest.toString());

        dialog.sendRequest(sipProvider.getNewClientTransaction(byeRequest));
    }

    /**
     * builds and sends a standard OK message with SDP.
     * @return serverTransaction ServerTransaction for call
     * @param isa InetSocketAddress of conference receiver
     * @throws ParsEexception if message cannot be parsed
     * @throws SipException if general sip exception occurs.
     * @throws TransactionDoesNotExistException if transaction can
     *         not be found.
     */
    public void sendOkWithSdp(Request request,
        ServerTransaction st, InetSocketAddress isa, SdpInfo remoteSdpInfo)
        throws TransactionDoesNotExistException, ParseException,
            SipException {

    Response response = messageFactory.createResponse(Response.OK, request);

    ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);

        if (to == null) {
        Logger.println("something is wrong, no ToHeader...");
        return;
    }

        try {
            if (to.getTag() == null || to.getTag().trim().length() == 0) {
        Dialog dialog = st.getDialog();
                to.setTag(Integer.toString(dialog.hashCode()));
            }
    } catch (ParseException ex) {
        Logger.println("can't set to tag");
        return;
    }

        /*
     * Contact Header (where subsequent requests should be sent to)
         * e.g. Contact: "Awarenex" <sip:Awarenex@152.70.1.43:5060>;
         *   where   "Awarenex" <sip:... == (local Address)
         */
    Address address = (Address) to.getAddress();

        SipURI toURI = (SipURI) address.getURI();

        SipURI contactURI =
        addressFactory.createSipURI(toURI.getUser(), ourPublicIpAddress);

    contactURI.setPort(ourPublicSipPort);

        Address contactAddress = addressFactory.createAddress(contactURI);

    contactAddress.setDisplayName(to.getName());

        ContactHeader contactHeader =
        headerFactory.createContactHeader(contactAddress);

    response.addHeader(contactHeader);

    String mySdp;

    try {
        mySdp = sdpManager.generateSdp("MeetingCentral", isa, remoteSdpInfo);
    } catch (IOException e) {
        throw new SipException("Failed to generate sdp "
        + remoteSdpInfo.getMediaInfo());
    }

        ContentTypeHeader contentTypeHeader = headerFactory.
            createContentTypeHeader("application", "sdp");

        response.setContent(mySdp, contentTypeHeader);

    try {
        Logger.writeFile(response.toString());
            st.sendResponse(response);
    } catch (InvalidArgumentException e) {
        Logger.println("SendOKWithSdp:  " + e.getMessage());
        throw new SipException("Failed to send response:  " + e.getMessage());
    }
    }

    /*
     * Send an OK with no SDP
     */
    public void sendOK(Request request, ServerTransaction st)
            throws TransactionDoesNotExistException, ParseException,
            SipException {

    Response response = messageFactory.createResponse(Response.OK,
        request);

    ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);

        if (to == null) {
        Logger.println("something is wrong, no to header...");
        return;
    }

        try {
            if (to.getTag() == null || to.getTag().trim().length() == 0) {
        Dialog dialog = st.getDialog();
                to.setTag(Integer.toString(dialog.hashCode()));
            }
    } catch (ParseException ex) {
        Logger.println("can't set to tag");
        return;
    }

        /*
     * Contact Header (where subsequent requests should be sent to)
         * e.g. Contact: "Awarenex" <sip:Awarenex@152.70.1.43:5060>;
         *   where   "Awarenex" <sip:... == (local Address)
         */
    Address address = (Address) to.getAddress();

        SipURI toURI = (SipURI) address.getURI();

        SipURI contactURI =
        addressFactory.createSipURI(toURI.getUser(), ourPublicIpAddress);

    contactURI.setPort(ourPublicSipPort);

        Address contactAddress = addressFactory.createAddress(contactURI);

    contactAddress.setDisplayName(to.getName());

        ContactHeader contactHeader =
        headerFactory.createContactHeader(contactAddress);

    response.addHeader(contactHeader);

    try {
            st.sendResponse(response);
        } catch (InvalidArgumentException e) {
            Logger.println("SendOk:  " + e.getMessage());
            throw new SipException("Failed to send response:  " + e.getMessage());
        }
    }

    /**
     * builds and sends a BUSY message.
     * @param clientTransaction ClientTransaction for call
     * @return serverTransaction ServerTransaction for call
     * @throws ParsEexception if message cannot be parsed
     * @throws SipException if general sip exception occurs.
     * @throws TransactionDoesNotExistException if transaction can
     *         not be found.
     */
    public void sendBusy(Request request,
        ServerTransaction serverTransaction)
            throws TransactionDoesNotExistException, ParseException,
            SipException {

    Response response = messageFactory.createResponse(Response.BUSY_HERE,
        request);

        /*
     * Contact Header (where subsequent requests should be sent to)
         * e.g. Contact: "Awarenex" <sip:Awarenex@152.70.1.43:5060>;
         *   where   "Awarenex" <sip:... == (local Address)
         */
    //SipURI contactUrl =
    //    addressFactory.createSipURI(cp.getPhoneNumber(), ourPublicIpAddress);
        //contactUrl.setPort(ourPublicSipPort);

        //SipURI contactURI =
    //    addressFactory.createSipURI(cp.getPhoneNumber(), ourPublicIpAddress);

    //contactURI.setPort(port);

        //Address contactAddress =
        //    addressFactory.createAddress(contactURI);

    //contactAddress.setDisplayName(cp.getName());

        //ContactHeader contactHeader =
    //    headerFactory.createContactHeader(contactAddress);

    //response.addHeader(contactHeader);

    try {
            serverTransaction.sendResponse(response);
        } catch (InvalidArgumentException e) {
            Logger.println("SendBusy:  " + e.getMessage());
            throw new SipException("Failed to send response:  " + e.getMessage());
        }
    }

    public SdpInfo getSdpInfo(String sdpBody) throws ParseException {
    return getSdpInfo(sdpBody, true);
    }

    public SdpInfo getSdpInfo(String sdpBody, boolean isRequest)   throws ParseException
    {
    SdpInfo remoteSdpInfo = sdpManager.parseSdp(sdpBody);

    MediaInfo myPreferredMediaInfo = sdpManager.getPreferredMediaInfo();

    byte payload;

    Logger.writeFile("My preferred media " + myPreferredMediaInfo);

    /*
     * If this is a remote SIP REQUEST and the remote side supports our
     * preferred mediaInfo, we will reply selecting our preferred media.
     * Otherwise, we reply using the remote's media choice, which is
     * either the remote's preferred or the "best" choice.
     */
    if (isRequest && remoteSdpInfo.isSupported(myPreferredMediaInfo)) {
        payload = myPreferredMediaInfo.getPayload();
        remoteSdpInfo.setMediaInfo(myPreferredMediaInfo);
        Logger.println("My preferred payload being used " + payload);
    } else {
        if (isRequest) {
            Logger.writeFile("My preferred media " + myPreferredMediaInfo + " not supported...");
        }

        try {
            payload = remoteSdpInfo.getMediaInfo().getPayload();
            remoteSdpInfo.setMediaInfo(sdpManager.findMediaInfo(payload));
            Logger.writeFile("media setting is " + remoteSdpInfo.getMediaInfo());

        } catch (ParseException e) {
            remoteSdpInfo.setMediaInfo(new MediaInfo((byte)0, RtpPacket.PCMU_ENCODING, 8000, 1, false));
        }
    }
    return remoteSdpInfo;
    }

    /*
     * We add an attribute to the sdp data when we send a request
     * to a sip phone number
     */
    public static String getCallIdFromSdp(Request request) {
    byte[] rawContent = request.getRawContent();

    if (rawContent == null) {
        return null;
    }

        String sdpBody = new String(rawContent);

    SdpInfo sdpInfo;

    try {
        sdpInfo = SdpManager.parseSdp(sdpBody);
    } catch (ParseException e) {
        return null;
    }

    return sdpInfo.getCallId();
    }

    /*
     * We add an attribute to the sdp data when we send a request
     * to a sip phone number
     */
    public static String getConferenceIdFromSdp(Request request) {
    byte[] rawContent = request.getRawContent();

    if (rawContent == null) {
        return null;
    }

        String sdpBody = new String(rawContent);

    SdpInfo sdpInfo;

    try {
        sdpInfo = SdpManager.parseSdp(sdpBody);
    } catch (ParseException e) {
        return null;
    }

    return sdpInfo.getConferenceId();
    }

    public static String getUserNameFromSdp(Request request) {
        byte[] rawContent = request.getRawContent();

        if (rawContent == null) {
            return null;
        }

        String sdpBody = new String(rawContent);

        SdpInfo sdpInfo;

        try {
            sdpInfo = SdpManager.parseSdp(sdpBody);
        } catch (ParseException e) {
            return null;
        }

        return sdpInfo.getUserName();
    }

    public static boolean getDistributedBridgeFromSdp(Request request) {
       byte[] rawContent = request.getRawContent();

        if (rawContent == null) {
            return false;
        }

        String sdpBody = new String(rawContent);

        SdpInfo sdpInfo;

        try {
            sdpInfo = SdpManager.parseSdp(sdpBody);
        } catch (ParseException e) {
            return false;
        }

        return sdpInfo.isDistributedBridge();
    }

    /*
     * Utility method to parse a request and get the requestor's phone number
     */
    public static String getFromPhoneNumber(Object requestEvent) {
    Request request = ((RequestEvent)requestEvent).getRequest();

    FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);

    Address address = from.getAddress();

    SipURI uri = (SipURI)address.getURI();

    if (uri.toString().indexOf("@") >= 0) {
        String s = uri.getUser() + "@" + uri.getHost();

        if (uri.getPort() != -1) {
        s += ":" + uri.getPort();
        }

        return s;
    }

    return ((SipURI)address.getURI()).getUser();
    }

    public static String getRequest(Object requestEvent) {
    Request request = ((RequestEvent)requestEvent).getRequest();

    return request.toString();
    }

    /*
     * Utility method to parse a request and get the To phone number
     */
    public static String getToPhoneNumber(Object requestEvent) {
    Request request = ((RequestEvent)requestEvent).getRequest();

    ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);

    Address address = to.getAddress();

    return ((SipURI)address.getURI()).getUser();
    }

    public static String getPhoneNumber(String phoneNumber) {
    if (SipUtil.isSipUri(phoneNumber) == false) {
        return phoneNumber;
    }

    try {
            Address address = addressFactory.createAddress(phoneNumber);
            SipURI sipURI = (SipURI)address.getURI();
            return sipURI.getUser();
    } catch (ParseException e) {
            Logger.println("parse exception:  " + phoneNumber);
        return phoneNumber;
        }
    }

    /*
     * Utility method to parse a request and get the requestor's host address
     */
    public static String getFromHost(Object requestEvent) {
    Request request = ((RequestEvent)requestEvent).getRequest();

    FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);

    Address address = (Address) from.getAddress();

    return ((SipURI)address.getURI()).getHost();
    }

    /*
     * Utility method to parse a request and get the requestor's name
     */
    public static String getFromName(Object requestEvent) {
    Request request = ((RequestEvent)requestEvent).getRequest();

    FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);

    Address address = (Address) from.getAddress();

    String name = address.getDisplayName();

    if (name == null) {
        name = address.getURI().toString();
    }

    return name;
    }

    public static void sendAckWithSDP(ClientTransaction ct, String sdp)
                throws ParseException, SipException
    {
        Dialog dialog = ct.getDialog();
        Request ackRequest = dialog.createRequest(Request.ACK);
        if(sdp != null){
            ContentTypeHeader contentTypeHeader;
            contentTypeHeader =
        headerFactory.createContentTypeHeader("application", "sdp");
            ackRequest.setContent(sdp, contentTypeHeader);
        }

        dialog.sendAck(ackRequest);
        return;
    }

}
