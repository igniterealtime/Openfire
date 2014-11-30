/**
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

import java.io.IOException;
import java.text.*;
import java.util.*;
import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;

import org.slf4j.*;
import org.slf4j.Logger;

import org.jitsi.videobridge.openfire.*;

class RegisterProcessing implements SipListener
{
    private static final Logger Log = LoggerFactory.getLogger(RegisterProcessing.class);
    private Request registerRequest = null;
    private boolean isRegistered = false;

    private Timer reRegisterTimer = new Timer();

    private String registrar;
    private String address;
    private ProxyCredentials proxyCredentials;
    private VideoBridgeSipListener.SipServerCallback sipServerCallback;
    private String sipCallId;

    int registrarPort = 5060;

    int expires = 120;

    private HeaderFactory headerFactory = SipService.getHeaderFactory();
    private AddressFactory addressFactory = SipService.getAddressFactory();
    private MessageFactory messageFactory = SipService.getMessageFactory();
    private SipProvider sipProvider = SipService.getSipProvider();

    public RegisterProcessing(String address, String registrar, ProxyCredentials proxyCredentials)
    {
		Log.info("Start registering...." + registrar);

		this.registrar = registrar;
		this.proxyCredentials = proxyCredentials;
		this.address = address;

		sipServerCallback = VideoBridgeSipListener.getSipServerCallback();

		try {
			register();
		} catch (IOException e) {
			Log.info(e.getMessage());
		}
    }

    public void processRequest(RequestEvent requestReceivedEvent) {
	Log.info("Request ignored:  "
	    + requestReceivedEvent.getRequest());
    }

    public void processResponse(ResponseEvent responseReceivedEvent) {

		//Log.info("Registering response...." + sipCallId);

		Response response = (Response)responseReceivedEvent.getResponse();
        int statusCode = response.getStatusCode();
        String method = ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getMethod();

		Log.debug("Got response " + response);

		if (statusCode == Response.OK) {
				isRegistered = true;

			Log.info("Voice bridge successfully registered with "	+ registrar + " for " + proxyCredentials.getXmppUserName());
			//OfMeetPlugin.sipRegisterStatus = "Registered ok with " + proxyCredentials.getHost();

        	sipServerCallback.removeSipListener(sipCallId);

		} else if (statusCode == Response.UNAUTHORIZED || statusCode == Response.PROXY_AUTHENTICATION_REQUIRED) {

            if (method.equals(Request.REGISTER))
            {
                CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

            	if (cseq.getSequenceNumber() < 2) {

					ClientTransaction regTrans = SipService.handleChallenge(response, responseReceivedEvent.getClientTransaction(), proxyCredentials);

					if (regTrans != null)
					{
						try {
							regTrans.sendRequest();

						} catch (Exception e) {

							Log.info("Registration failed, cannot send transaction " + e);
							//OfMeetPlugin.sipRegisterStatus = "Registration error " + e.toString();
						}

					} else {
						Log.info("Registration failed, cannot create transaction");
						//OfMeetPlugin.sipRegisterStatus = "Registration cannot create transaction";
					}

                } else {
                    Log.info("Registration failed " + responseReceivedEvent);
					//OfMeetPlugin.sipRegisterStatus = "Registration failed";
				}
            }

		} else {
			Log.info("Unrecognized response:  " + response);
		}

    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
	Log.info("Timeout trying to register with " + registrar);
        sipServerCallback.removeSipListener(sipCallId);
    }

    public void processDialogTerminated(DialogTerminatedEvent dte) {

        Log.debug("processDialogTerminated called");
        sipServerCallback.removeSipListener(sipCallId);
    }

    public void  processTransactionTerminated(TransactionTerminatedEvent tte) {
        Log.debug("processTransactionTerminated called");
        sipServerCallback.removeSipListener(sipCallId);
    }

    public void  processIOException(IOExceptionEvent ioee) {
        Log.debug("processTransactionTerminated called");
        sipServerCallback.removeSipListener(sipCallId);
    }

    private void register() throws IOException
    {
		Log.info("Registering with " + registrar);
        FromHeader fromHeader = getFromHeader();
        Address fromAddress = fromHeader.getAddress();

        //Request URI
        SipURI requestURI = null;
        try {
            requestURI = addressFactory.createSipURI(null, registrar);

		} catch (ParseException e) {
            throw new IOException("Bad registrar address:" + registrar + " " + e.getMessage());
        }
        //requestURI.setPort(registrarPort);


        try {
            requestURI.setTransportParam(
			sipProvider.getListeningPoint().getTransport());

        } catch (ParseException e) {
            throw new IOException(sipProvider.getListeningPoint().getTransport() + " is not a valid transport! " + e.getMessage());
        }

        CallIdHeader callIdHeader = sipProvider.getNewCallId();
        CSeqHeader cSeqHeader = null;

        try {
            cSeqHeader = headerFactory.createCSeqHeader(1, Request.REGISTER);
        } catch (ParseException e) {
            //Should never happen
            throw new IOException("Corrupt Sip Stack " + e.getMessage());
        } catch (InvalidArgumentException e) {
            //Should never happen
            throw new IOException("The application is corrupt "  );
        }

        ToHeader toHeader = null;
        try {
            String proxyWorkAround =
                System.getProperty("com.sun.mc.softphone.REGISTRAR_WORKAROUND");

            if (proxyWorkAround != null &&
                    proxyWorkAround.toUpperCase().equals("TRUE")) {

                SipURI toURI = (SipURI)(requestURI.clone());
                toURI.setUser(System.getProperty("user.name"));

                toHeader = headerFactory.createToHeader(
		    addressFactory.createAddress(toURI), null);
            } else {
                toHeader = headerFactory.createToHeader(fromAddress, null);
            }
        } catch (ParseException e) {
            throw new IOException("Could not create a To header for address:"
                + fromHeader.getAddress() + " " + e.getMessage());
        }



        ArrayList viaHeaders = getLocalViaHeaders();
        MaxForwardsHeader maxForwardsHeader = getMaxForwardsHeader();
        Request request = null;

        try {
            request = messageFactory.createRequest(requestURI,
                Request.REGISTER,
                callIdHeader,
                cSeqHeader, fromHeader, toHeader,
                viaHeaders,
                maxForwardsHeader);
        } catch (ParseException e) {
            throw new IOException("Could not create the register request! " + e.getMessage());
		}

        ExpiresHeader expHeader = null;

        for (int retry = 0; retry < 2; retry++) {
            try {
                expHeader = headerFactory.createExpiresHeader(
                    expires);
            } catch (InvalidArgumentException e) {
                if (retry == 0) {
                    continue;
                }
                throw new IOException(
                    "Invalid registrations expiration parameter - "
                    + expires + " " + e.getMessage());
            }
        }

        request.addHeader(expHeader);
        ContactHeader contactHeader = getRegistrationContactHeader();
		request.addHeader(contactHeader);

		try {
			SipURI routeURI = (SipURI) addressFactory.createURI("sip:" + proxyCredentials.getProxy() + ";lr");
			RouteHeader routeHeader = headerFactory.createRouteHeader(addressFactory.createAddress(routeURI));
			request.addHeader(routeHeader);

		} catch (Exception e) {

			Log.error("Creating registration route error ", e);
		}

        ClientTransaction regTrans = null;
        try {
            regTrans = sipProvider.getNewClientTransaction(request);
        } catch (TransactionUnavailableException e) {
            throw new IOException("Could not create a register transaction!\n" + "Check that the Registrar address is correct! " + e.getMessage());
        }

        try {
			sipCallId = callIdHeader.getCallId();
			sipServerCallback.addSipListener(sipCallId, this);
			registerRequest = request;
            regTrans.sendRequest();

			Log.debug("Sent register request " + registerRequest);

			if (expires > 0) {
					scheduleReRegistration();
			}
        } catch (Exception e) {
	    	throw new IOException("Could not send out the register request! "+ e.getMessage());
        }

        this.registerRequest = request;
    }

    public void unregister() throws IOException
    {
        if (!isRegistered) {
            return;
        }

        cancelPendingRegistrations();
        isRegistered = false;

        if (this.registerRequest == null) {
            Log.info("Couldn't find the initial register request");
            throw new IOException("Couldn't find the initial register request");
        }

        Request unregisterRequest = (Request) registerRequest.clone();

        try {
            unregisterRequest.getExpires().setExpires(0);
            CSeqHeader cSeqHeader =
                (CSeqHeader)unregisterRequest.getHeader(CSeqHeader.NAME);
            //[issue 1] - increment registration cseq number
            //reported by - Roberto Tealdi <roby.tea@tin.it>
            cSeqHeader.setSequenceNumber(cSeqHeader.getSequenceNumber()+1);
        } catch (InvalidArgumentException e) {
            Log.info("Unable to set Expires Header " + e.getMessage());
	    return;
        }

        ClientTransaction unregisterTransaction = null;

        try {
            unregisterTransaction = sipProvider.getNewClientTransaction(
		unregisterRequest);
        } catch (TransactionUnavailableException e) {
            throw new IOException("Unable to create a unregister transaction "
		+ e.getMessage());
        }
        try {
            unregisterTransaction.sendRequest();
        } catch (SipException e) {
            Log.info("Faied to send unregister request "
		+ e.getMessage());
	    return;
        }
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    private FromHeader fromHeader;

    private FromHeader getFromHeader() throws IOException {

	if (fromHeader != null) {
	    return fromHeader;
        }

	try {
	    SipURI fromURI = (SipURI) addressFactory.createURI("sip:" + proxyCredentials.getUserName() + "@" + registrar);

            fromURI.setTransportParam(sipProvider.getListeningPoint().getTransport());

            fromURI.setPort(sipProvider.getListeningPoint().getPort());

            Address fromAddress = addressFactory.createAddress(fromURI);

            fromAddress.setDisplayName(proxyCredentials.getUserDisplay());

	    fromHeader = headerFactory.createFromHeader(fromAddress, Integer.toString(hashCode()));

        } catch (ParseException e) {
            throw new IOException(
		"A ParseException occurred while creating From Header! "
		+ e.getMessage());
        }

	return fromHeader;
    }

    private ArrayList viaHeaders;

    private ArrayList getLocalViaHeaders() throws IOException {
	/*
         * We can't keep a cached copy because the callers
         * of this method change the viaHeaders.  In particular
         * a branch may be added which causes INVITES to fail.
         */
        if (viaHeaders != null) {
            return viaHeaders;
        }

        ListeningPoint lp = sipProvider.getListeningPoint();
	viaHeaders = new ArrayList();

        try {
	    String addr = lp.getIPAddress();

            ViaHeader viaHeader = headerFactory.createViaHeader(
                addr, lp.getPort(), lp.getTransport(), null);

	    viaHeader.setRPort();

            viaHeaders.add(viaHeader);
            return viaHeaders;
        } catch (ParseException e) {
            throw new IOException (
		"A ParseException occurred while creating Via Headers! "
		+ e.getMessage());
        } catch (InvalidArgumentException e) {
            throw new IOException(
		"Unable to create a via header for port " + lp.getPort()
                    + " " + e.getMessage());
        }
    }

    private static final int  MAX_FORWARDS = 70;
    private MaxForwardsHeader maxForwardsHeader;

    private MaxForwardsHeader getMaxForwardsHeader() throws IOException {
        if (maxForwardsHeader != null) {
	    return maxForwardsHeader;
        }

	try {
            maxForwardsHeader =
		headerFactory.createMaxForwardsHeader(MAX_FORWARDS);
            return maxForwardsHeader;
        } catch (InvalidArgumentException e) {
                throw new IOException(
                    "A problem occurred while creating MaxForwardsHeader "
		    + e.getMessage());
        }
    }

    private ContactHeader contactHeader;

    private ContactHeader getRegistrationContactHeader() throws IOException {
        if (contactHeader != null) {
            return contactHeader;
        }

        try {
            SipURI contactURI = (SipURI) addressFactory.createURI("sip:" + proxyCredentials.getUserName() + "@" + proxyCredentials.getHost());

            contactURI.setTransportParam(
		sipProvider.getListeningPoint().getTransport());
	    contactURI.setPort(sipProvider.getListeningPoint().getPort());
            Address contactAddress = addressFactory.createAddress(contactURI);
            contactAddress.setDisplayName(proxyCredentials.getUserDisplay());
            contactHeader = headerFactory.createContactHeader(contactAddress);
            return contactHeader;
	} catch (ParseException e) {
             throw new IOException(
                    "A ParseException occurred while creating From Header! "
		+ " " + e.getMessage());
        }
    }

	class ReRegisterTask extends TimerTask {

        public ReRegisterTask() {
        }

        public void run() {
            try {
                if (isRegistered()) {
                    register();
		}
            } catch (IOException e) {
                Log.info("Failed to reRegister " + e.getMessage());
            }
        }
    }

    private void cancelPendingRegistrations() {
        reRegisterTimer.cancel();
        reRegisterTimer = null;

        reRegisterTimer = new Timer();
    }

    private void scheduleReRegistration() {
        ReRegisterTask reRegisterTask = new ReRegisterTask();
        reRegisterTimer.schedule(reRegisterTask, expires * 1000);
    }
}
