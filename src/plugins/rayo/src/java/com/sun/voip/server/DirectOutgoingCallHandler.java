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

import java.text.ParseException;
import javax.sip.*;
import javax.sip.header.*;
import javax.sip.message.*;
import javax.sip.address.*;
import com.sun.voip.CallParticipant;
import com.sun.voip.Logger;

/**
 *
 * @author jerry
 */
public class DirectOutgoingCallHandler implements SipListener {
    public static final int INVITE_SENT = 1;
    public static final int INVITE_OK = 2;
    public static final int INVITE_ACK = 3;
    public static final int TERMINATED = 4;
    CallParticipant callParticipant;
    int state;
    ClientTransaction ct;
    String sdp;
    Object stateLock = new Object();
    SipUtil sipUtil;
    DirectOutgoingCallHandler otherCall;
    String sipCallId;
    
    /** Creates a new instance of DirectOutgoingCallHandler */
    public DirectOutgoingCallHandler(CallParticipant cp) {
        callParticipant = cp;
	sipUtil = new SipUtil();
    }

    /**
     * Sends an Invite with the given SDP
     * If the sdp is null, then sends an Invite with no sdp
     * @param sdp String the SDP to send with the INVITE
     */
    
    public void sendInvite(String sdp){
        try {
 
            ct = sipUtil.sendInvite(callParticipant, sdp);
            CallIdHeader callIdHeader = (CallIdHeader) ct.getRequest().getHeader(CallIdHeader.NAME);
            sipCallId = callIdHeader.getCallId();
            SipServer.getSipServerCallback().addSipListener(sipCallId, this);
            setState(INVITE_SENT);
        } catch (InvalidArgumentException ex) {
            ex.printStackTrace();
        } catch (ParseException ex) {
            ex.printStackTrace();
        } catch (SipException ex) {
            ex.printStackTrace();
        }
    }
        
    public String waitForOK() throws Exception{
        synchronized(stateLock){
            while(getState() == INVITE_SENT){
                try {
                    
                    stateLock.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
            if(getState() == INVITE_OK){
                return sdp;
            }
            else{
                throw new Exception("Could not establish call");
            }
        }
    }
    
    /**
     * Sends an ACK with the given sdp
     * @param sdp String the SDP to use in the ACK
     */
    
    public void sendAck(String sdp){
        try {
            SipUtil.sendAckWithSDP(ct, sdp);
            setState(INVITE_ACK);
        } catch (SipException ex) {
            ex.printStackTrace();
        } catch (ParseException ex) {
            ex.printStackTrace();
        }
    }
    
    public void processResponse(ResponseEvent responseEvent) {
        Response resp = responseEvent.getResponse();
        int status = resp.getStatusCode();
        // We only handle the OK response for now
        if(status == Response.OK && getState() == INVITE_SENT){
            sdp = new String(resp.getRawContent());
            synchronized(stateLock){
                setState(INVITE_OK);
                stateLock.notify();
            }
        }
        if(status > 400){ // All status codes > 400 are errors
           synchronized(stateLock){
               setState(TERMINATED);
               stateLock.notifyAll();
           }
        }
    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        // Any timeout is considered a failure. We terminate the call
        sendBye();
        setState(TERMINATED);
        if(otherCall != null){
            otherCall.sendBye();
        }
    }

    public void processRequest(RequestEvent requestEvent) {
        Request req = requestEvent.getRequest();
        if(req.getMethod().equals(Request.BYE) && getState() == INVITE_ACK){
            setState(TERMINATED);
            if(otherCall != null){
                otherCall.sendBye();
            }
            SipServer.getSipServerCallback().removeSipListener(sipCallId);
        }
        try {
            sipUtil.sendOK(req, requestEvent.getServerTransaction());
        } catch (TransactionDoesNotExistException ex) {
            ex.printStackTrace();
        } catch (ParseException ex) {
            ex.printStackTrace();
        } catch (SipException ex) {
            ex.printStackTrace();
        }
    }
    
    public void setOtherCall(DirectOutgoingCallHandler ot){
        otherCall = ot;
    }
    public DirectOutgoingCallHandler getOtherCall(){
        return otherCall;
    }
 
    public void sendBye(){
        try {
            sipUtil.sendBye(ct);
            setState(TERMINATED);
            SipServer.getSipServerCallback().removeSipListener(sipCallId);
            
        } catch (TransactionDoesNotExistException ex) {
            ex.printStackTrace();
        } catch (InvalidArgumentException ex) {
            ex.printStackTrace();
        } catch (ParseException ex) {
            ex.printStackTrace();
        } catch (SipException ex) {
            ex.printStackTrace();
        }
    }

    public void waitForTerminate() {
        while(true){
            if(getState() == TERMINATED){
                return;
            }
            synchronized(stateLock){
                try {
                    stateLock.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
    
    public void setState(int st){
        synchronized(stateLock){
            state = st;
            stateLock.notifyAll();
        }
    }
    public int getState(){
        return state;
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
