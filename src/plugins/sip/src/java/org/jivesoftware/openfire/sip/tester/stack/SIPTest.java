/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.sip.tester.stack;

import org.jivesoftware.openfire.sip.tester.comm.CommunicationsListener;
import org.jivesoftware.openfire.sip.tester.comm.CommunicationsException;
import org.jivesoftware.openfire.sip.tester.Log;
import org.jivesoftware.openfire.sip.sipaccount.SipAccount;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Title: SIP Register Tester
 *
 * @author Thiago Rocha Camargo (thiago@jivesoftware.com)
 */

public class SIPTest implements CommunicationsListener {

    private SipAccount sipAccount;
    private InetAddress localAddress;
    private Result result = null;
    private SipManager sipManager = null;
    private List<TestListener> listeners = new ArrayList<TestListener>();

    public enum Result {
        Successfully, WrongUser, WrongPass, NetworkError, Trying, Timeout, Forbidden, WrongAuthUser
    }

    ;

    public SIPTest(InetAddress localAddress, SipAccount sipAccount) {
        this.sipAccount = sipAccount;
        this.localAddress = localAddress;
        sipManager = new SipManager(localAddress);
    }

    public Result getResult() {
        return result;
    }

    private void setResult(Result result) {
        if (!result.equals(this.result)) fireResultChanged(this.result, result);
        this.result = result;
    }

    public void test(int timeout) {
        test(timeout, 1);
    }

    public void test(int timeout, int tries) {

        for (int i = 0; i < tries && (getResult() == null || Result.Timeout.equals(getResult())); i++) {

            if (sipManager.isStarted()) try {
                sipManager.stop();
            } catch (CommunicationsException e) {
                e.printStackTrace();
            }


            setResult(Result.Trying);

            SIPConfig.setRegistrarAddress(sipAccount.getServer());
            SIPConfig.setAuthenticationRealm(sipAccount.getServer());
            SIPConfig.setDefaultDomain(sipAccount.getServer());

            try {
                sipManager.start();
                sipManager.addCommunicationsListener(this);
                try {
                    sipManager.startRegisterProcess(sipAccount.getSipUsername(), sipAccount.getAuthUsername(), sipAccount.getPassword());
                } catch (CommunicationsException e) {
                    setResult(Result.NetworkError);
                    Log.error(e);
                }

                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    Log.error(e);
                }

                if (getResult().equals(Result.Trying)) setResult(Result.Timeout);

            } catch (CommunicationsException e) {
                setResult(Result.NetworkError);
                Log.error(e);
            } finally {
                try {
                    sipManager.stop();
                } catch (CommunicationsException e) {
                    Log.error(e);
                }
            }
        }
    }

    public void fireResultChanged(Result old, Result current) {
        for (TestListener listener : listeners)
            listener.resultChanged(old, current);
    }

    public void addListener(TestListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TestListener listener) {
        listeners.remove(listener);
    }

    public void registered(RegistrationEvent evt) {
        setResult(Result.Successfully);
        try {
            sipManager.unregister();
        } catch (CommunicationsException e) {
            Log.error(e);
        }
    }

    public void registering(RegistrationEvent evt) {
    }

    public void registrationFailed(RegistrationEvent evt) {
        if (evt.getType().equals(RegistrationEvent.Type.NotFound))
            setResult(Result.WrongUser);
        else if (evt.getType().equals(RegistrationEvent.Type.WrongPass))
            setResult(Result.WrongPass);
        else if (evt.getType().equals(RegistrationEvent.Type.Forbidden))
            setResult(Result.Forbidden);
        else if (evt.getType().equals(RegistrationEvent.Type.WrongAuthUser))
            setResult(Result.WrongAuthUser);
    }

    public void unregistering(RegistrationEvent evt) {
    }

    public void unregistered(RegistrationEvent evt) {
        try {
            sipManager.stop();
            Log.debug("Stopped");
        } catch (CommunicationsException e) {
            Log.error(e);
        }
    }

    public static void main(String args[]) {

        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            SipAccount sipAccount = new SipAccount("", "", "", "", "", "", "", false);

            SIPTest sipTest = new SIPTest(localAddress, sipAccount);

            sipTest.test(3000, 2);

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }


    }
}
