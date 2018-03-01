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

import com.sun.voip.CallState;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.SdpInfo;
import com.sun.voip.RtpPacket;

import javax.sip.*;
import javax.sip.header.*;
import javax.sip.message.*;
import javax.sip.address.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.text.ParseException;

import java.util.Vector;

/**
 * SipIncomingCallAgent handles calls from outside directed to us.
 * We are acting as a user agent server and handle INVITE requests.
 *
 * We reply to the INVITE with an OK and the port number of a new conference
 * with just this call.  The purpose of starting a new conference is to
 * watch for dtmf keys identifying the person and the real conference to join.
 */
public class SipIncomingCallAgent extends CallSetupAgent implements SipListener {
    private ServerTransaction st;

    private SipServer.SipServerCallback sipServerCallback;

    private SipUtil sipUtil;

    /*
     * Maintain a list of all sipCallId's for incoming calls
     * so we can detect duplicate INVITES.
     */
    private static Vector sipCallIds = new Vector();

    private String sipCallId;		// The sipCallId for this agent

    private boolean receivedBye;

    /**
     * Constructor
     */
    public SipIncomingCallAgent(CallHandler callHandler, Object o) {
    super(callHandler);

        sipServerCallback = SipServer.getSipServerCallback();

        MediaInfo mixerMediaPreference = callHandler.getConferenceManager().getMediaInfo();

        sipUtil = new SipUtil(mixerMediaPreference);

        handleInvite((RequestEvent)o);
    }

    /**
     * Processes SIP requests.  The only request being handled is INVITE.
     * @param requestEvent the event containing the SIP request
     */
    public synchronized void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();

    if (request.getMethod().equals(Request.ACK)) {
        handleAck(request);
    } else if (request.getMethod().equals(Request.BYE)) {
        handleBye(requestEvent);
    } else if (request.getMethod().equals(Request.CANCEL)) {
        handleBye(requestEvent);
        } else {
            // no other requests should come in other than ACK or BYE or CANCEL
            Logger.error("SipIncomingCallAgent:  ignoring request "
        + request.getMethod());
        //terminateCall();		// just ignore for now : BAO
        }
    }

    /**
     * handles an INVITE request
     * @param request the request
     * @param transId the transaction Id
     * @throws TransactionDoesNotExistException when the transaction
     *         record does not exist.
     *
     * Here's what we need to do:
     * Create a temporary conference for this call.  Add this member
     * to the conference.  Send an OK reply with the socket address
     * of the conference receiver.
     */
    private void handleInvite(RequestEvent requestEvent) {

        setState(CallState.INVITED);

        Request request = requestEvent.getRequest();
        FromHeader fromHeader =	(FromHeader) request.getHeader(FromHeader.NAME);
        ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);

        String from = fromHeader.getAddress().toString();
        String to = toHeader.getAddress().toString();

        CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);

        String sipCallId = callIdHeader.getCallId();
        this.sipCallId = sipCallId;

        Logger.println("SipIncomingCallAgent:  Got an INVITE from " + from + " to " + to);
        Logger.writeFile(request.toString());

        if (Logger.logLevel >= Logger.LOG_SIP) {
            Logger.println("SipIncomingCallAgent:  Adding listener for call id " + sipCallId);
        }

        try {
            sipServerCallback.addSipListener(sipCallId, this);
            answerCall(requestEvent);

        } catch (Exception e) {
            Logger.println("SipIncomingCallAgent:  " + request);
        e.printStackTrace();
        terminateCall();
        }
    }

    private void handleAck(Request request) {
    Logger.writeFile("SipIncomingCallAgent:  Got ack...");
    Logger.writeFile(request.toString());

    if (getState() != CallState.ESTABLISHED) {
        try {
            processSdp(request);
        } catch (ParseException e) {
        Logger.error("SipIncomingCallAgent:  " + e.getMessage());
        terminateCall();
        }

        ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
        String s = "ToAddress='" + toHeader.getAddress().toString() + "'";

        s += " IncomingCall='true'";

        setState(CallState.ESTABLISHED, s);
    }
    }

    private void handleBye(RequestEvent requestEvent) {
    Request request = requestEvent.getRequest();

    Logger.writeFile("SipIncomingCallAgent got BYE or CANCEL");
    Logger.writeFile(request.toString());

        try {
            CallIdHeader callIdHeader = (CallIdHeader)
                request.getHeader("Call-Id");

            String sipCallId = callIdHeader.getCallId();

            if (sipCallId.equals(this.sipCallId)) {
                receivedBye = true;

        removeSipCallId(sipCallId);

                try {
                    Logger.println("Call " + cp + " has hung up.");

                    sipUtil.sendOK(request,
            requestEvent.getServerTransaction());
                } catch (Exception e) {
                    /*
                     * We sometimes get a null ServerTransaction
                     */
                }
                cancelRequest("hung up");
                sipServerCallback.removeSipListener(sipCallId);
            } else {
                /*
                 * this should not happen since the message has been
                 * delegated to this sip agent.
                 */
                throw new TransactionDoesNotExistException(
                    cp + "BYE request received did not "
                    + "match our callId:  " + request);
            }
        } catch (TransactionDoesNotExistException e) {
            Logger.error("Call " + cp
                + " Transaction not found " + e.getMessage());
        } catch (SipException e) {
            Logger.exception("Call " + cp + " SIP Stack error", e);
            cancelRequest("handleBye:  SIP Stack error " + e.getMessage());
        } catch (Exception e) {
            Logger.exception("Call " + cp + " Unknown error", e);
            cancelRequest("handleBye:  SIP Stack error " + e.getMessage());
        }
    }

    private SdpInfo processSdp(Request request) throws ParseException {
    byte[] rawSdp = request.getRawContent();

    if (rawSdp == null) {
        return null;
    }

    String sdpBody = new String(rawSdp);

    SdpInfo sdpInfo = sipUtil.getSdpInfo(sdpBody);

    String remoteHost = sdpInfo.getRemoteHost();
    int remotePort = sdpInfo.getRemotePort();

    Logger.println(
        "SipIncomingCallAgent:  remote socket " + remoteHost + " "
        + remotePort);

    try {
            InetSocketAddress isa =
        new InetSocketAddress(remoteHost, remotePort);

        setEndpointAddress(isa, sdpInfo.getMediaInfo().getPayload(),
        sdpInfo.getTransmitMediaInfo().getPayload(),
        sdpInfo.getTelephoneEventPayload());
    } catch (Exception e) {
        Logger.println("SipIncomingCallAgent:  can't create isa");
        throw new ParseException("SipIncomingCallAgent:  can't create isa", 0);
    }

    return sdpInfo;
    }

    /**
     * Processes SIP responses.
     * @param responseEvent the event containing the SIP response
     */
    public synchronized void processResponse(
        ResponseEvent responseEvent) {

        try {
            Response response = (Response)responseEvent.getResponse();

            int statusCode = response.getStatusCode();

        FromHeader fromHeader = (FromHeader)
        response.getHeader(FromHeader.NAME);

            String displayName = fromHeader.getAddress().getDisplayName();

        /*
         * We don't expect any responses from the other side.
         */
        Logger.println("SipIncomingCallAgent: Response ignored:  statusCode "
                + statusCode + " fromHeader " + displayName
            + " " + response);
        } catch (Exception e) {
            Logger.exception("SipIncomingCallAgent:  processResponse:  ", e);
        }
    }

    /**
     * Processes a retransmit or expiration Timeout of an underlying
     * {@link Transaction} handled by this SipListener. This Event notifies the
     * application that a retransmission or transaction Timer expired in the
     * SipProvider's transaction state machine. The TimeoutEvent encapsulates
     * the specific timeout type and the transaction identifier either client
     * or server upon which the timeout occured. The type of Timeout can by
     * determined by:
     * <code>timeoutType = timeoutEvent.getTimeout().getValue();</code>
     *
     * @param timeoutEvent - the timeoutEvent received indicating either the
     * message retransmit or transaction timed out.
     */
    public void processTimeout(TimeoutEvent timeoutEvent) {
    }


    public void answerCall(RequestEvent requestEvent) {

        setState(CallState.INVITED);

        Request request = requestEvent.getRequest();

        FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
        ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);

        String from = fromHeader.getAddress().toString();
        String to = toHeader.getAddress().toString();

        Logger.println("SipIncomingCallAgent:  Accept call " + from + " to " + to);
        Logger.writeFile(request.toString());

        try {
            SdpInfo sdpInfo = processSdp(request);

            st = requestEvent.getServerTransaction();

            if (st == null) {
                st = SipServer.getSipProvider().getNewServerTransaction(request);
            }

            InetSocketAddress isa = callHandler.getReceiveAddress();

            if (isa == null) {
                Logger.println("SipIncomingCallAgent:  can't get receiver socket!");
                terminateCall();
                return;
            }

            setState(CallState.ANSWERED);

            if (Logger.logLevel >= Logger.LOG_SIP) {
                Logger.println("SipIncomingCallAgent:  sending ok");
            }

            sipUtil.sendOkWithSdp(request, st, isa, sdpInfo);

        } catch (Exception e) {
            Logger.println("SipIncomingCallAgent:  " + request);
            e.printStackTrace();
            terminateCall();
        }
    }



    public void terminateCall() {
    if (receivedBye) {
        return;
    }

    if (sipCallId != null) {
            sipServerCallback.removeSipListener(sipCallId);

        removeSipCallId(sipCallId);
    }

        if (getState() == CallState.INVITED) {
            try {
                Logger.writeFile("SipIncomingCallAgent:  sendCancel:  " + cp);
                sipUtil.sendCancel(st);
            } catch (Exception e) {
            }
        } else {
            try {
                Logger.writeFile("sendBye:  " + cp);
                sipUtil.sendBye(st);
            } catch (Exception ex) {
            }
        }
    }

    /*
     * Check for duplicate sipCallid.  If it's a new call,
     * add it to the list
     */
    public static boolean addSipCallId(String sipCallId) {
        synchronized(sipCallIds) {
            for (int i = 0; i < sipCallIds.size(); i++) {
                String id = (String) sipCallIds.elementAt(i);

                if (id.equals(sipCallId)) {
                    return false;
                }
            }

            sipCallIds.add(sipCallId);  // add to list of all coming calls
        }
    return true;
    }


    private void removeSipCallId(String sipCallId) {
    synchronized(sipCallIds) {
        for (int i = 0; i < sipCallIds.size(); i++) {
            String id = (String)sipCallIds.elementAt(i);

        if (sipCallId.equals(id)) {
            sipCallIds.remove(i);
        }
        }
    }
    }

    public void processDialogTerminated(DialogTerminatedEvent dte) {
        if (Logger.logLevel >= Logger.LOG_SIP) {
            Logger.println("processDialogTerminated called");
    }
    }

    public void  processTransactionTerminated(TransactionTerminatedEvent tte) {
        if (Logger.logLevel >= Logger.LOG_SIP) {
        Logger.println("processTransactionTerminated called");
    }
    }

    public void  processIOException(IOExceptionEvent ioee) {
        if (Logger.logLevel >= Logger.LOG_SIP) {
        Logger.println("processTransactionTerminated called");
    }
    }

}
