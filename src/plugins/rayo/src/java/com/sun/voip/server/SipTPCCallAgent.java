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

import com.sun.voip.CallEvent;
import com.sun.voip.CallState;
import com.sun.voip.Logger;
import com.sun.voip.MediaInfo;
import com.sun.voip.SdpInfo;
import com.sun.voip.TreatmentManager;

import javax.sip.*;
import javax.sip.header.*;
import javax.sip.message.*;
import javax.sip.address.*;
import javax.sip.InvalidArgumentException;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.io.IOException;

import java.text.ParseException;

import java.util.Vector;

/**
 * Third Party Call Control SIP User Agent.
 *
 *    SipTPCCallAgent	     VoIPGateway or SIP proxy
 *
 *        |(1) INVITE with SDP    |
 *        |---------------------->|
 *        |(2) 200 OK with SDP	  |
 *        |<----------------------|
 *        |(3) ACK with SDP	  |
 *	  |---------------------->|
 *        |(4) treatment then RTP |
 *        |......................>|
 */
public class SipTPCCallAgent extends CallSetupAgent implements SipListener {

    private SipServer.SipServerCallback sipServerCallback;

    private SipUtil sipUtil;

    private ClientTransaction clientTransaction;  // most recent client trans
    private String sdpBody;
    private String sipCallId;
    private boolean receivedBye;

    private boolean callAnswered = false;

    private static boolean forceGatewayError = false;

    private boolean ackSent = false;

    private boolean gotOk;

    private TreatmentManager busyTreatment;

    /**
     * Constructor
     * @param callHandler CallHandler this CallParticipant is associated with
     */
    public SipTPCCallAgent(CallHandler callHandler) {
    super(callHandler);

    MediaInfo mixerMediaPreference = callHandler.getConferenceManager().getMediaInfo();

    sipUtil = new SipUtil(mixerMediaPreference);
    }

    public static void forceGatewayError(boolean forceGatewayError) {
    SipTPCCallAgent.forceGatewayError = forceGatewayError;
    }

    /*
     * Begin Third-Party Call Control.
     */
    public void initiateCall() throws IOException {
        try {
        try {
                busyTreatment = new TreatmentManager("busy.au", 0);
        } catch (IOException e) {
        Logger.println("Invalid busy treatment:  " + e.getMessage());
        }

            Logger.writeFile("Call " + cp
        + ":   Begin SIP third party call");

            setState(CallState.INVITED);

        InetSocketAddress isa = callHandler.getReceiveAddress();

        if (isa == null) {
        throw new IOException("can't get receiver socket!");
        }

            // send INVITE to the CallParticipant
            clientTransaction = sipUtil.sendInvite(cp, isa);

        if (clientTransaction == null) {
                Logger.error("Error placing call:  " + cp);
        setState(CallState.ENDED, "Reason='Error placing call'");
        throw new IOException("Error placing call:  " + cp);
        }

            CallIdHeader callIdHeader = (CallIdHeader)
                clientTransaction.getRequest().getHeader(CallIdHeader.NAME);

            sipCallId = callIdHeader.getCallId();

            sipServerCallback = SipServer.getSipServerCallback();
        sipServerCallback.addSipListener(sipCallId, this);
    } catch (java.text.ParseException e) {
            Logger.println("Call " + cp + " Error placing call " + cp +":  "
        +  e.getMessage());
        setState(CallState.ENDED, "Reason='Error placing call " + cp + " "
        + e.getMessage() + "'");
        throw new IOException("Error placing call " + cp + " "
        + e.getMessage());
    } catch (InvalidArgumentException e) {
            Logger.println("Call " + cp + " Error placing call " + cp +":  "
        +  e.getMessage());
        setState(CallState.ENDED, "Reason='Error placing call " + cp + " "
        + e.getMessage() + "'");
        throw new IOException("Error placing call " + cp + " "
        + e.getMessage());
    } catch (SipException e) {
            Logger.println("Call " + cp + " Error placing call " + cp +":  "
        +  e.getMessage());
        setState(CallState.ENDED, "Reason='Error placing call " + cp + " "
        + e.getMessage() + "'");
        throw new IOException("Error placing call " + cp + " "
        + e.getMessage());
    }
    }

    /**
     * Done with treatments for call end, now terminate the call
     */
    public void terminateCall() {
    if (receivedBye == false) {
        if (gotOk == false ||
            (getState() == CallState.INVITED && callAnswered == false)) {

            try {
            Logger.writeFile("Call " + cp + ":  sendCancel");
                    sipUtil.sendCancel(clientTransaction);
            } catch (Exception e) {
            Logger.println("sendCancel " + e.getMessage());
            }
        } else {
        /*
         * Try sending a BYE as well.
         * Seems that when we treat SESSION_PROGRESS
         * as OK, sometimes we need to send a CANCEL
         * and other times a BYE.  We'll send both.
         */
            try {
            Logger.writeFile("Call " + cp + ":  sendBye");
                sipUtil.sendBye(clientTransaction);
            } catch (Exception e) {
            Logger.println("Call " + cp + ":  sendBye"
            + e.getMessage());
            }
        }
    }
    }

    /**
     * Processes SIP requests.  The only request being handled is BYE.
     * @param requestReceivedEvent the event containing the SIP request
     */
    public synchronized void processRequest(RequestEvent requestReceivedEvent) {

        // obtain request and transaction id
        Request request = requestReceivedEvent.getRequest();

        ServerTransaction st = requestReceivedEvent.getServerTransaction();

        if (request.getMethod().equals(Request.BYE)) {
            handleBye(request, st);
        } else if (request.getMethod().equals(Request.INVITE)) {
        /*
         * This is a re-Invite
         */
        handleReInvite(request, st);
        } else if (request.getMethod().equals(Request.ACK)) {
        Logger.println("Call " + cp + " got ACK");
        } else {
            // no other requests should come in other than BYE, INVITE or ACK
            Logger.writeFile("Call " + cp
        + " ignoring request " + request.getMethod());
        }
    }

    /**
     * handles a BYE request
     * @param request the request
     * @param transId the transaction Id
     * @throws TransactionDoesNotExistException when the transaction
     *         record does not exist.
     */
    private void handleBye(Request request, ServerTransaction st) {
        try {
            CallIdHeader callIdHeader = (CallIdHeader)
        request.getHeader("Call-Id");

            String sipCallId = callIdHeader.getCallId();

            if (sipCallId.equals(this.sipCallId)) {
        receivedBye = true;

        try {
            Logger.writeFile("Call " + cp + " has hung up.");

            //sipUtil.sendOK(clientTransaction, st, cp);
            sipUtil.sendOK(request, st);
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
                    + "match either party:  " + request);
            }
        } catch (TransactionDoesNotExistException e) {
            Logger.error("Call " + cp
        + " Transaction not found " + e.getMessage());
        } catch (SipException e) {
            Logger.exception("Call " + cp + " SIP Stack error", e);
        cancelRequest("handleBye:  SIP Stack error " + e.getMessage());
        } catch (Exception e) {
            Logger.exception("Call " + cp + " Unknown error ", e);
        cancelRequest("handleBye:  SIP Stack error " + e.getMessage());
        }
    }

    private void handleReInvite(Request request, ServerTransaction st) {
    Logger.println("Call " + cp + " Re-INVITE\n" + request);

        if (request.getRawContent() == null) {
            Logger.error("Call " + cp + " no SDP in INVITE Request!");
            return;
        }

        String sdpBody = new String(request.getRawContent());

    SdpInfo sdpInfo;

    try {
        sdpInfo = sipUtil.getSdpInfo(sdpBody);
    } catch (ParseException e) {
        Logger.error("Call " + cp + " invalid SDP in re-INVITE Request! "
        + e.getMessage());
        return;
    }

    MediaInfo mediaInfo = sdpInfo.getMediaInfo();

        InetSocketAddress isa = new InetSocketAddress(
        sdpInfo.getRemoteHost(), sdpInfo.getRemotePort());

    InetSocketAddress rtcpAddress = sdpInfo.getRtcpAddress();

        setEndpointAddress(isa, mediaInfo.getPayload(),
        sdpInfo.getTransmitMediaInfo().getPayload(),
        sdpInfo.getTelephoneEventPayload(), rtcpAddress);

        isa = callHandler.getReceiveAddress();

        try {
            sipUtil.sendOkWithSdp(request, st, isa, sdpInfo);
        } catch (Exception e) {
            Logger.println("Call " + cp +
        " Failed to send ok with sdp for re-invite " + e.getMessage());
            return;
        }
    }

    /**
     * Processes SIP responses.
     * @param responseReceivedEvent the event containing the SIP response
     *
     * Note:  This method is called by a thread from the SIP stack.
     *        If it blocks, no other events will be delivered!
     */
    public static boolean rejectCall = false;	// for debugging!

    public synchronized void processResponse(
        ResponseEvent responseReceivedEvent) {

        try {
            Response response = (Response)responseReceivedEvent.getResponse();
            ClientTransaction clientTransaction =
        responseReceivedEvent.getClientTransaction();

            int statusCode = response.getStatusCode();

        FromHeader fromHeader = (FromHeader)
        response.getHeader(FromHeader.NAME);

            String displayName = fromHeader.getAddress().getDisplayName();

        if (Logger.logLevel >= Logger.LOG_SIP) {
            Logger.println("Response:  statusCode "
                + statusCode + " state " + getCallState()
            + " fromHeader " + displayName + " call participant "
            + cp.getName());
        }

        if (reasonCallTerminated != null) {
        /*
         * Ignore OK and Request Terminated.
         * XXX what's the symbol for 487?
         */
        if (statusCode != Response.OK && statusCode != 487) {
            if (Logger.logLevel >= Logger.LOG_SIP) {
                Logger.println("Call " + cp
                + ":  request cancelled, ignoring response");
            }
        }

                CallIdHeader callIdHeader = (CallIdHeader)
            response.getHeader("Call-Id");

                String sipCallId = callIdHeader.getCallId();
        sipServerCallback.removeSipListener(sipCallId);
        return;
        }

            /*
         * Some type of global failure that prevents the
             * CallParticipant from being contacted, report failure.
             */
        if (forceGatewayError) {
        statusCode = 500;
        forceGatewayError = false;
        }

            if (statusCode >= 500 && getState() == CallState.INVITED) {
                Logger.error("Call " + cp + " gateway error:  " + statusCode
            + " " + response.getReasonPhrase());
        cancelRequest("gateway error: " + statusCode + " "   + response.getReasonPhrase());
                return;

            } else if (statusCode == Response.PROXY_AUTHENTICATION_REQUIRED || statusCode == Response.UNAUTHORIZED) {

                if (cp.getProxyCredentials() != null)
                {
                    try {
                        SipServer.handleChallenge(response, clientTransaction, cp.getProxyCredentials()).sendRequest();

                    } catch (Exception e) {

                        Logger.println("Proxy authentification failed  " + e);
                    }
                }
                return;


            } else if (statusCode >= 400) {

                // if we get a busy or an unknown error, play busy.
                Logger.println("Call " + cp  + " got status code :" + statusCode);

                cp.setCallEndTreatment(null);
                cp.setConferenceJoinTreatment(null);
                cp.setConferenceLeaveTreatment(null);

                /*
                 * play busy treatment, but deallocate any resources
                 * held up by ringBack first, if any.
                 */
                //stopCallAnsweredTreatment();

            if (statusCode == Response.BUSY_HERE) {
            try {
            if (busyTreatment != null) {
                addTreatment(busyTreatment);
                            //busyTreatment.waitForTreatment();
            } else {
                Logger.println("Unable to play busy treatment!!!");
            }
            } catch (Exception e) {
                Logger.error("can't start busy treatment!" + sdpBody);
            }

            CallEvent callEvent = new CallEvent(CallEvent.BUSY_HERE);

            callEvent.setInfo(response.getReasonPhrase());

            sendCallEventNotification(callEvent);
        }

        //sipUtil.sendBye(clientTransaction);
                cancelRequest(response.getReasonPhrase());
                return;
            }

            /* state machine */
            switch (getState()) {
            /*
         * CallParticipant picked up, send treatment if any,
         * and wait for it to finish.
         */
            case CallState.INVITED:
        if (rejectCall) {
                    Logger.error("Call " + cp + " gateway error:  "
            + statusCode + " " + response.getReasonPhrase());

            cancelRequest("gateway error: " + statusCode + " "
                + response.getReasonPhrase());
                    return;
        }

                handleCallParticipantInvited(response, clientTransaction);
                break;

        /*
         * Call established, the ACK needs to be resent.
         * According to Ranga, this is done by the NIST SIP Stack.
         */
        case CallState.ESTABLISHED:
        if (statusCode == Response.OK) {
            gotOk = true;

                    Logger.writeFile("Call " + cp + " Got OK, ESTABLISHED");

            if (ackSent == false) {
                        sipUtil.sendAck(clientTransaction);
                ackSent = true;
            }
        }
        break;

        case CallState.ENDED:
        break;		// ignore the response

            default:
        Logger.error("Process Response bad state " + getState()
            + "\n" + response);
            }
        } catch (SipException e) {
            Logger.exception("Call " + cp + " SIP Stack error ", e);

        cancelRequest(
        "processResponse:  SIP Stack error " + e.getMessage());
        } catch (Exception e) {
            Logger.exception("processResponse:  " + cp, e);

        cancelRequest(
        "processResponse:  SIP Stack error " + e.getMessage());
        }
    }

    /**
     * handles the INVITED state.
     * @param response the response
     * @param clientTransaction the client transaction
     * @throws SipException SIP stack related error
     */
    private void handleCallParticipantInvited(Response response,  ClientTransaction clientTransaction) throws ParseException, SipException,    InvalidArgumentException
    {
        FromHeader fromHeader = (FromHeader)
        response.getHeader(FromHeader.NAME);

        String displayName = fromHeader.getAddress().getDisplayName();

        int statusCode = response.getStatusCode();

        Logger.println("handleCallParticipantInvited " + cp	+ " status " + statusCode + " " + response.getReasonPhrase());
        Logger.println("handleCallParticipantInvited , displayname " + displayName);

        CallIdHeader callIdHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);

        if (sipCallId.equals(callIdHeader.getCallId()) &&
            displayName.equals(cp.getDisplayName()) &&
            (statusCode == Response.OK || statusCode == Response.SESSION_PROGRESS) &&
            ((CSeqHeader)response.getHeader(CSeqHeader.NAME)).getMethod().equals(Request.INVITE))
        {
            if (statusCode == Response.SESSION_PROGRESS) {
                /*
                * For some calls, we never get an OK.  Instead we just get
                * SESSION_PROGRESS.  In order to handle these calls, we treat
                * SESSION_PROGRESS as OK.  If an OK arrives later, we'll
                * send an ACK.  This flag allows us to enable or
                * disable this workaround for each call.
                *
                * The problem with always treating SESSION_PROGRESS as OK
                * is that in a conference everybody will hear the ringing sound
                * which the remote call sends until the call is actually answered.
                * This can be avoided if joinConfirmation is specified.
                * The other problem is that if we treat SESSION_PROGRESS
                * as though the call has been answered, then we'll start
                * playing the treatment before a person really answers to
                * hear the treatment.
                */

                if (cp.getHandleSessionProgress() == false) {
                    Logger.writeFile("Call " + cp + " Ignoring SESSION_PROGRESS");
                    return;
                }

                Logger.writeFile("Call " + cp + " Treating SESSION_PROGRESS as OK");
            }

            if (response.getRawContent() == null)
            {
                Logger.error("Call " + cp + " no SDP in OK Response!");
                cancelRequest("SIP error!  no SDP in OK Response!");
                return;
            }

            this.clientTransaction = clientTransaction;

            if (statusCode == Response.OK)
            {
                gotOk = true;
                Logger.writeFile("Call " + cp + " Got OK, call answered\n" + response);
            }

            ToHeader toHeader = (ToHeader)response.getHeader(ToHeader.NAME);

                /*
             * We got an OK response.
             *
             * send an ACK back to the CallParticipant
                 */

            if (statusCode == Response.OK) {
                sipUtil.sendAck(clientTransaction);
                ackSent = true;
            }

            if (callAnswered) {
                Logger.writeFile("Call " + cp + " done processing OK");
                return;
            }

            /*
             * Remember the IP and port of where to send data to
             * the CallParticipant.
             */

            sdpBody = new String(response.getRawContent());

            SdpInfo sdpInfo;

            try {
                sdpInfo = sipUtil.getSdpInfo(sdpBody, false);

            } catch (ParseException e) {

                Logger.error("Call " + cp + " Invalid SDP in OK Response! "	+ e.getMessage());
                cancelRequest("SIP error!  Invalid SDP in OK Response!");
                return;
            }

            MediaInfo mediaInfo = sdpInfo.getMediaInfo();
            InetSocketAddress isa = new InetSocketAddress(sdpInfo.getRemoteHost(), sdpInfo.getRemotePort());
            InetSocketAddress rtcpAddress = sdpInfo.getRtcpAddress();
            setEndpointAddress(isa, mediaInfo.getPayload(),sdpInfo.getTransmitMediaInfo().getPayload(),sdpInfo.getTelephoneEventPayload(), rtcpAddress);

            /*
             * The CallParticipant has answered.
             * If join confirmation is required, we remain in the
             * INVITED state.  We set the callAnswered flag so that
             * if the join confirmation times out we know to
             * send a BYE rather than a CANCEL.
             */

            callAnswered = true;

            if (cp.getJoinConfirmationTimeout() == 0) {
                setState(CallState.ANSWERED);
            }

            /*
             * Start treatment if any and wait for it to finish.
             * When the treatment finishes, notification will
             * be delivered to our parent which will indicate
             * we're ready for the conference.
             *
             * If there's no treatment to be played, we're ready now
             * unless we're waiting for join confirmation..
             */
            initializeCallAnsweredTreatment();

            if (callAnsweredTreatment != null) {
                startCallAnsweredTreatment();
            } else {

                if (cp.getJoinConfirmationTimeout() == 0) {
                        setState(CallState.ESTABLISHED);
                }
            }

        } else {
            Logger.writeFile("Call " + cp + " Ignoring response: " + response.getReasonPhrase());

            if (Logger.logLevel >= Logger.LOG_SIP) {
                    Logger.println("Call " + cp + " Response: " + response);
            }
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
