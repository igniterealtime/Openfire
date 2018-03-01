/*
 * Copyright (C) 2012 Issa Gorissen <issa-gorissen@usa.net>. All rights reserved.
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
package org.jivesoftware.openfire.crowd;

import java.rmi.RemoteException;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthProvider;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Auth provider for Atlassian Crowd
 */
public class CrowdAuthProvider implements AuthProvider {
    private static final Logger LOG = LoggerFactory.getLogger(CrowdAuthProvider.class);
    
    private CrowdManager manager = null;
    
    public CrowdAuthProvider() {
        try {
            manager = CrowdManager.getInstance();
        } catch (Exception e) {
            LOG.error("Failure to load the Crowd manager", e);
        }
    }

    /**
     * Returns if the username and password are valid; otherwise this
     * method throws an UnauthorizedException.<p>
     *
     * @param username the username or full JID.
     * @param password the password
     * @throws UnauthorizedException if the username and password do
     *      not match any existing user.
     * @throws ConnectionException it there is a problem connecting to user and group sytem
     * @throws InternalUnauthenticatedException if there is a problem authentication Openfire itself into the user and group system
     */
    @Override
    public void authenticate(String username, String password) throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException {
        if (manager == null) {
            throw new ConnectionException("Unable to connect to Crowd");
        }

        if (username == null || password == null || "".equals(password.trim())) {
            throw new UnauthorizedException();
        }

        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain. Return authentication failed.
                throw new UnauthorizedException();
            }
        }

        try {
            manager.authenticate(username, password);
        } catch (RemoteException re) {
            throw new UnauthorizedException();
        }
    }

    @Override
    public String getPassword(String username) throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Retrieve password not supported by this version of authentication provider");
    }

    @Override
    public void setPassword(String username, String password) throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Setting password not implemented by this version of authentication provider");
    }

    @Override
    public boolean supportsPasswordRetrieval() {
        return false;
    }

    @Override
    public boolean isScramSupported() {
        // TODO Auto-generated method stub
        return false;
    }


    @Override
    public String getSalt(String username) throws UnsupportedOperationException, UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getIterations(String username) throws UnsupportedOperationException, UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getServerKey(String username) throws UnsupportedOperationException, UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getStoredKey(String username) throws UnsupportedOperationException, UserNotFoundException {
        throw new UnsupportedOperationException();
    }
}
