/**
 *    Copyright 2012 Voxbone SA/NV
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ifsoft.sip;

import org.jivesoftware.util.*;

import java.text.*;
import java.util.*;

import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;

import org.slf4j.*;
import org.slf4j.Logger;

/**
 * Functions useful for building SIP messages
 *
 */
public class SipService
{
    private static final Logger Log = LoggerFactory.getLogger(SipService.class);
	public static AddressFactory addressFactory;
	public static HeaderFactory headerFactory;
	public static MessageFactory messageFactory;
	public static VideoBridgeSipListener sipListener;
	public static SipProvider sipProvider;
	public static ProxyCredentials sipAccount;
	private SipStack sipStack = null;
	private SipFactory sipFactory;
	private int localport;
	private static String localip;
	private static String remoteip;
	private static String agentName;
	private static String clientVersion;

	public SipService(Properties properties)
	{
		localip = properties.getProperty("com.voxbone.kelpie.ip");
		localport = Integer.parseInt(properties.getProperty("com.voxbone.kelpie.sip_port", "5060"));
		remoteip = properties.getProperty("com.voxbone.kelpie.sip_gateway");
		sipListener = new VideoBridgeSipListener(properties.getProperty("com.voxbone.kelpie.hostname"));

		agentName = properties.getProperty("com.voxbone.kelpie.sip.user-agent", "Openfire Meetings");
		clientVersion = properties.getProperty("com.voxbone.kelpie.version");
		String nameOS = System.getProperty("os.name");
		String versOS = System.getProperty("os.version");
		agentName = agentName + " " + clientVersion + " (" + nameOS + "/" + versOS + ")";

		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("gov.nist");
		properties.setProperty("javax.sip.STACK_NAME", "ofmeetsip");

		try
		{
			sipStack = sipFactory.createSipStack(properties);
			headerFactory = sipFactory.createHeaderFactory();
			addressFactory = sipFactory.createAddressFactory();
			messageFactory = sipFactory.createMessageFactory();
		}
		catch (PeerUnavailableException e)
		{
			Log.error(e.toString(), e);
		}

		try
		{
			ListeningPoint udp = sipStack.createListeningPoint(localip, localport, "udp");
			sipProvider = sipStack.createSipProvider(udp);
			sipProvider.setAutomaticDialogSupportEnabled(false);
			sipProvider.addSipListener(sipListener);

			registerWithDefaultProxy();
		}
		catch (TransportNotSupportedException e)
		{
			Log.error(e.toString(), e);
		}
		catch (InvalidArgumentException e)
		{
			Log.error(e.toString(), e);
		}
		catch (ObjectInUseException e)
		{
			Log.error(e.toString(), e);
		}
		catch (TooManyListenersException e)
		{
			Log.error(e.toString(), e);
		}
	}

    public static MessageFactory getMessageFactory()
    {
        return messageFactory;
    }

    public static SipProvider getSipProvider()
    {
        return sipProvider;
    }

    public static AddressFactory getAddressFactory()
    {
        return addressFactory;
    }

    public static HeaderFactory getHeaderFactory()
    {
        return headerFactory;
    }

	public static String getRemoteIP()
	{
		return remoteip;
	}

	public static String getLocalIP()
	{
		ListeningPoint lp = sipProvider.getListeningPoint(ListeningPoint.UDP);
		return lp.getIPAddress();
	}

	public void stop()
	{
		if (sipStack != null) sipStack.stop();
	}

	private void registerWithDefaultProxy()
	{
		sipAccount = new ProxyCredentials();

		try {
			String authusername = JiveGlobals.getProperty("voicebridge.default.proxy.sipauthuser", null);
			String server = JiveGlobals.getProperty("voicebridge.default.proxy.sipserver", null);

			if (authusername != null && server != null && authusername.equals("") == false && server.equals("") == false)
			{
				String name = JiveGlobals.getProperty("voicebridge.default.proxy.name", "admin");
				String username = JiveGlobals.getProperty("voicebridge.default.proxy.username", authusername);
				String sipusername = JiveGlobals.getProperty("voicebridge.default.proxy.sipusername", authusername);
				String displayname = JiveGlobals.getProperty("voicebridge.default.proxy.sipdisplayname", authusername);
				String password = JiveGlobals.getProperty("voicebridge.default.proxy.sippassword", authusername);
				String stunServer = JiveGlobals.getProperty("voicebridge.default.proxy.stunserver", localip);
				String stunPort = JiveGlobals.getProperty("voicebridge.default.proxy.stunport", "3478");
				String voicemail = JiveGlobals.getProperty("voicebridge.default.proxy.voicemail", name);
				String outboundproxy = JiveGlobals.getProperty("voicebridge.default.proxy.outboundproxy", localip);

				sipAccount.setName(username);
				sipAccount.setXmppUserName(name);
				sipAccount.setUserName(sipusername);
				sipAccount.setAuthUserName(authusername);
				sipAccount.setUserDisplay(displayname);
				sipAccount.setPassword(password.toCharArray());
				sipAccount.setHost(server);
				sipAccount.setProxy(outboundproxy);
				sipAccount.setRealm(server);

				Log.info(String.format("VoiceBridge adding SIP registration: %s with user %s host %s", sipAccount.getXmppUserName(), sipAccount.getUserName(), sipAccount.getHost()));
				new RegisterProcessing(localip, server, sipAccount);
			}

        } catch (Exception e) {
			Log.error("registerWithDefaultProxy", e);
		}
	}

	public static boolean acceptCall(CallSession cs)
	{
		try
		{
			Request req = cs.inviteTransaction.getRequest();
			Response resp = messageFactory.createResponse(Response.OK, cs.inviteTransaction.getRequest());
			ContentTypeHeader cth = headerFactory.createContentTypeHeader("application", "sdp");
			Object sdp = cs.buildSDP(false);

			ToHeader th = (ToHeader) req.getHeader("To");
			String dest = ((SipURI) th.getAddress().getURI()).getUser();

			ListeningPoint lp = sipProvider.getListeningPoint(ListeningPoint.UDP);

			Address localAddress = addressFactory.createAddress("sip:" + dest + "@" + lp.getIPAddress() + ":" + lp.getPort());

			ContactHeader ch = headerFactory.createContactHeader(localAddress);

			AllowHeader allowHeader = headerFactory.createAllowHeader("INVITE, ACK, CANCEL, OPTIONS, BYE, UPDATE, NOTIFY, MESSAGE, SUBSCRIBE, INFO");
			resp.addHeader(allowHeader);

			resp.addHeader(ch);

			UserAgentHeader userAgent = (UserAgentHeader) headerFactory.createHeader(UserAgentHeader.NAME, agentName);
			resp.setHeader(userAgent);

			resp.setContent(sdp, cth);
			cs.inviteTransaction.sendResponse(resp);
		}
		catch (ParseException e)
		{
			Log.error("Error accepting call", e);
			return false;
		}
		catch (SipException e)
		{
			Log.error("Error accepting call", e);
			return false;
		}
		catch (InvalidArgumentException e)
		{
			Log.error("Error accepting call", e);
			return false;
		}
		return true;
	}

	public static boolean sendBye(CallSession cs)
	{
		Request req;
		try
		{
			if (cs.inviteOutTransaction != null && (cs.sipDialog.getState() == null || cs.sipDialog.getState() == DialogState.EARLY))
			{
				req = cs.inviteOutTransaction.createCancel();
				ClientTransaction t = sipProvider.getNewClientTransaction(req);
				t.sendRequest();
				cs.sendBye();
				return false;
			}
			else
			{
				if (cs.sipDialog != null)
				{
					req = cs.sipDialog.createRequest(Request.BYE);
					ClientTransaction t = sipProvider.getNewClientTransaction(req);
					cs.sipDialog.sendRequest(t);
					cs.sendBye();
				}
			}
		}
		catch (SipException e)
		{
			Log.error("Error sending BYE", e);
		}

		return true;
	}

	public static boolean sendReject(CallSession cs)
	{
		try
		{
			Request req = cs.inviteTransaction.getRequest();
			Response resp = messageFactory.createResponse(Response.TEMPORARILY_UNAVAILABLE, cs.inviteTransaction.getRequest());

			ToHeader th = (ToHeader) req.getHeader("To");
			String dest = ((SipURI) th.getAddress().getURI()).getUser();

			ListeningPoint lp = sipProvider.getListeningPoint(ListeningPoint.UDP);

			Address localAddress = addressFactory.createAddress("sip:" + dest + "@" + lp.getIPAddress() + ":" + lp.getPort());

			ContactHeader ch = headerFactory.createContactHeader(localAddress);
			resp.addHeader(ch);

			cs.inviteTransaction.sendResponse(resp);
		}
		catch (Exception e)
		{
			Log.error("Error sending Reject", e);
		}

		return true;
	}


	public static boolean sendDTMFinfo(CallSession cs, char dtmf, int dtmfl)
	{
		Request req;
		try
		{
			Log.debug("Sending SIP INFO DTMF - Signal: " + dtmf + " Duration:" + dtmfl);
			ContentTypeHeader cth = headerFactory.createContentTypeHeader("application", "dtmf-relay");
			String body = 	"Signal=" + dtmf + "\r\nDuration="+dtmfl;

			req = cs.sipDialog.createRequest(Request.INFO);
			ClientTransaction t = sipProvider.getNewClientTransaction(req);
			req.setContent(body, cth);
			cs.sipDialog.sendRequest(t);
		}
		catch (SipException e)
		{
			Log.error("Error sending DTMF INFO", e);
		}
		catch (ParseException e)
		{
			Log.error("Error sending DTMF INFO", e);
		}

		return true;
	}

	public static boolean sendVideoUpdate(CallSession cs)
	{
		Request req;
		try
		{
			ContentTypeHeader cth = headerFactory.createContentTypeHeader("application", "media_control+xml");
			String body = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
			            + "<media_control>"
			            +   "<vc_primitive>"
			            +     "<to_encoder>"
			            +       "<picture_fast_update>"
			            +       "</picture_fast_update>"
			            +     "</to_encoder>"
			            +   "</vc_primitive>"
			            + "</media_control>";

			req = cs.sipDialog.createRequest(Request.INFO);
			ClientTransaction t = sipProvider.getNewClientTransaction(req);
			req.setContent(body, cth);
			cs.sipDialog.sendRequest(t);
		}
		catch (SipException e)
		{
			Log.error("Error sending FVR INFO", e);
		}
		catch (ParseException e)
		{
			Log.error("Error sending FVR INFO", e);
		}

		return true;
	}

	public static boolean sendInvite(CallSession cs)
	{
		FromHeader fromHeader = null;
		ToHeader toHeader = null;
		URI requestURI = null;
		URI fromURI = null;

		try
		{
			ListeningPoint lp = sipProvider.getListeningPoint(ListeningPoint.UDP);
			localip = lp.getIPAddress();

			requestURI = addressFactory.createURI(cs.jabberRemote);
			toHeader = headerFactory.createToHeader(addressFactory.createAddress(requestURI), null);
			fromURI = addressFactory.createURI(cs.jabberLocal);
			fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(fromURI), null);

			int tag = (int) (Math.random() * 100000);
			fromHeader.setTag(Integer.toString(tag));

			ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
			ViaHeader viaHeader = null;

			viaHeader = headerFactory.createViaHeader(lp.getIPAddress(), lp.getPort(), lp.getTransport(), null);
			viaHeaders.add(viaHeader);

			CallIdHeader callIdHeader = sipProvider.getNewCallId();
			CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
			MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");

			Request request = messageFactory.createRequest(requestURI, Request.INVITE, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards, contentTypeHeader, cs.buildSDP(true));

			Address localAddress = addressFactory.createAddress(requestURI);

			ContactHeader ch = headerFactory.createContactHeader(localAddress);
			request.addHeader(ch);

			AllowHeader allowHeader = headerFactory.createAllowHeader("INVITE, ACK, CANCEL, OPTIONS, BYE, UPDATE, NOTIFY, MESSAGE, SUBSCRIBE, INFO");
			request.addHeader(allowHeader);

			UserAgentHeader userAgent = (UserAgentHeader) headerFactory.createHeader(UserAgentHeader.NAME, agentName);
			request.setHeader(userAgent);

			ClientTransaction t = sipProvider.getNewClientTransaction(request);

			//t.setApplicationData(new ResponseInfo(listener, transaction));

			Dialog d = SipService.sipProvider.getNewDialog(t);
			cs.sipDialog = d;
			d.setApplicationData(cs);

			t.setApplicationData(cs);
			cs.inviteOutTransaction = t;
			t.sendRequest();

			return true;
		}
		catch (ParseException e)
		{
			Log.error("Error on SIPTransmitter:deliverMessage", e);
		}
		catch (InvalidArgumentException e)
		{
			Log.error("Error on SIPTransmitter:deliverMessage", e);
		}
		catch (TransactionUnavailableException e)
		{
			Log.error("Error on SIPTransmitter:deliverMessage", e);
		}
		catch (SipException e)
		{
			Log.error("Error on SIPTransmitter:deliverMessage", e);
		}

		return false;
	}

	public static boolean sendMessageMessage(String to, String from, String body)
	{
		FromHeader fromHeader = null;
		ToHeader toHeader = null;
		URI requestURI = null;
		URI fromURI = null;

		try
		{
			requestURI = addressFactory.createURI(to);
			toHeader = headerFactory.createToHeader(addressFactory.createAddress(requestURI), null);
			fromURI = addressFactory.createURI(from);
			fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(fromURI), null);

			int tag = (int) (Math.random() * 100000);
			fromHeader.setTag(Integer.toString(tag));

			ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
			ViaHeader viaHeader = null;

			ListeningPoint lp = sipProvider.getListeningPoint(ListeningPoint.UDP);

			viaHeader = headerFactory.createViaHeader(lp.getIPAddress(), lp.getPort(), lp.getTransport(), null);
			viaHeaders.add(viaHeader);

			CallIdHeader callIdHeader = sipProvider.getNewCallId();
			CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.MESSAGE);
			MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("text", "plain");

			Request request = messageFactory.createRequest(requestURI, "MESSAGE", callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards, contentTypeHeader, body);

			ClientTransaction t = sipProvider.getNewClientTransaction(request);

			t.sendRequest();

			return true;
		}
		catch (ParseException e)
		{
			Log.error("Error on SIPTransmitter:deliverMessage", e);
		}
		catch (InvalidArgumentException e)
		{
			Log.error("Error on SIPTransmitter:deliverMessage", e);
		}
		catch (TransactionUnavailableException e)
		{
			Log.error("Error on SIPTransmitter:deliverMessage", e);
		}
		catch (SipException e)
		{
			Log.error("Error on SIPTransmitter:deliverMessage", e);
		}

		return false;
	}

    public synchronized static ClientTransaction handleChallenge(Response challenge, ClientTransaction challengedTransaction, ProxyCredentials proxyCredentials)
    {
        try {

            String branchID = challengedTransaction.getBranchId();
            Request challengedRequest = challengedTransaction.getRequest();
            Request reoriginatedRequest = (Request) challengedRequest.clone();

            ListIterator authHeaders = null;

            if (challenge == null || reoriginatedRequest == null)
                throw new NullPointerException("A null argument was passed to handle challenge.");

            // CallIdHeader callId =
            // (CallIdHeader)challenge.getHeader(CallIdHeader.NAME);

            if (challenge.getStatusCode() == Response.UNAUTHORIZED)
                authHeaders = challenge.getHeaders(WWWAuthenticateHeader.NAME);

            else if (challenge.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED)
                authHeaders = challenge.getHeaders(ProxyAuthenticateHeader.NAME);

            if (authHeaders == null)
                throw new SecurityException("Could not find WWWAuthenticate or ProxyAuthenticate headers");

            // Remove all authorization headers from the request (we'll re-add
            // them
            // from cache)
            reoriginatedRequest.removeHeader(AuthorizationHeader.NAME);
            reoriginatedRequest.removeHeader(ProxyAuthorizationHeader.NAME);
            reoriginatedRequest.removeHeader(ViaHeader.NAME);

            // rfc 3261 says that the cseq header should be augmented for the
            // new
            // request. do it here so that the new dialog (created together with
            // the new client transaction) takes it into account.
            // Bug report - Fredrik Wickstrom

            CSeqHeader cSeq = (CSeqHeader) reoriginatedRequest.getHeader((CSeqHeader.NAME));
            cSeq.setSequenceNumber(cSeq.getSequenceNumber() + 1);
            reoriginatedRequest.setHeader(cSeq);

            ClientTransaction retryTran = sipProvider.getNewClientTransaction(reoriginatedRequest);

            WWWAuthenticateHeader authHeader = null;

            while (authHeaders.hasNext()) {
                authHeader = (WWWAuthenticateHeader) authHeaders.next();
                String realm = authHeader.getRealm();

                FromHeader from = (FromHeader) reoriginatedRequest.getHeader(FromHeader.NAME);
                URI uri = from.getAddress().getURI();

                AuthorizationHeader authorization = getAuthorization(reoriginatedRequest.getMethod(),
                		reoriginatedRequest.getRequestURI().toString(),
                        reoriginatedRequest.getContent() == null ? "" : reoriginatedRequest.getContent().toString(),
                        authHeader, proxyCredentials);

                reoriginatedRequest.addHeader(authorization);

                // if there was trouble with the user - make sure we fix it
                if (uri.isSipURI()) {
                    ((SipURI) uri).setUser(proxyCredentials.getUserName());
                    Address add = from.getAddress();
                    add.setURI(uri);
                    from.setAddress(add);
                    reoriginatedRequest.setHeader(from);

                    if (challengedRequest.getMethod().equals(Request.REGISTER))
                    {
                        ToHeader to = (ToHeader) reoriginatedRequest.getHeader(ToHeader.NAME);
                        add.setURI(uri);
                        to.setAddress(add);
                        reoriginatedRequest.setHeader(to);

                    }

                    Log.info("URI: " + uri.toString());
                }

                // if this is a register - fix to as well

            }

            return retryTran;
        }
        catch (Exception e) {
            Log.error("ClientTransaction handleChallenge error", e);
            return null;
        }
    }

    private synchronized static AuthorizationHeader getAuthorization(String method, String uri,
                                                 String requestBody, WWWAuthenticateHeader authHeader,
                                                 ProxyCredentials proxyCredentials) throws SecurityException {
        String response = null;
        try {
            Log.info("getAuthorization " + proxyCredentials.getAuthUserName());

            response = MessageDigestAlgorithm.calculateResponse(authHeader
                    .getAlgorithm(), proxyCredentials.getAuthUserName(),
                    authHeader.getRealm(), new String(proxyCredentials
                    .getPassword()), authHeader.getNonce(),
                    // TODO we should one day implement those two null-s
                    null,// nc-value
                    null,// cnonce
                    method, uri, requestBody, authHeader.getQop());
        }
        catch (NullPointerException exc) {
            throw new SecurityException(
                    "The authenticate header was malformatted");
        }

        AuthorizationHeader authorization = null;
        try {
            if (authHeader instanceof ProxyAuthenticateHeader) {
                authorization = headerFactory
                        .createProxyAuthorizationHeader(authHeader.getScheme());
            } else {
                authorization = headerFactory
                        .createAuthorizationHeader(authHeader.getScheme());
            }

            authorization.setUsername(proxyCredentials.getAuthUserName());
            authorization.setRealm(authHeader.getRealm());
            authorization.setNonce(authHeader.getNonce());
            authorization.setParameter("uri", uri);
            authorization.setResponse(response);

            if (authHeader.getAlgorithm() != null)
                authorization.setAlgorithm(authHeader.getAlgorithm());
            if (authHeader.getOpaque() != null)
                authorization.setOpaque(authHeader.getOpaque());

            authorization.setResponse(response);
        }
        catch (ParseException ex) {
			throw new SecurityException("Failed to create an authorization header!");
        }

        return authorization;
    }
}
