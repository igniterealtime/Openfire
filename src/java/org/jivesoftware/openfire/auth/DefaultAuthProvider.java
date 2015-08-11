/**
 * $RCSfile$
 * $Revision: 1116 $
 * $Date: 2005-03-10 20:18:08 -0300 (Thu, 10 Mar 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.auth;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import javax.security.sasl.SaslException;
import javax.xml.bind.DatatypeConverter;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default AuthProvider implementation. It authenticates against the <tt>ofUser</tt>
 * database table and supports plain text and digest authentication.
 *
 * Because each call to authenticate() makes a database connection, the
 * results of authentication should be cached whenever possible.
 *
 * @author Matt Tucker
 */
public class DefaultAuthProvider implements AuthProvider {

	private static final Logger Log = LoggerFactory.getLogger(DefaultAuthProvider.class);

	    private static final String LOAD_PASSWORD =
	            "SELECT plainPassword,encryptedPassword FROM ofUser WHERE username=?";
	    private static final String TEST_PASSWORD =
	            "SELECT plainPassword,encryptedPassword,iterations,salt,storedKey FROM ofUser WHERE username=?";
    private static final String UPDATE_PASSWORD =
            "UPDATE ofUser SET plainPassword=?, encryptedPassword=?, storedKey=?, serverKey=?, salt=?, iterations=? WHERE username=?";
    
    private static final SecureRandom random = new SecureRandom();

    /**
     * Constructs a new DefaultAuthProvider.
     */
    public DefaultAuthProvider() {

    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
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
            if (!checkPassword(username, password)) {
                throw new UnauthorizedException();
            }
        }
        catch (UserNotFoundException unfe) {
            throw new UnauthorizedException();
        }
        // Got this far, so the user must be authorized.
    }

    public void authenticate(String username, String token, String digest) throws UnauthorizedException {
        if (username == null || token == null || digest == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
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
            String password = getPassword(username);
            String anticipatedDigest = AuthFactory.createDigest(token, password);
            if (!digest.equalsIgnoreCase(anticipatedDigest)) {
                throw new UnauthorizedException();
            }
        }
        catch (UserNotFoundException unfe) {
            throw new UnauthorizedException();
        }
        // Got this far, so the user must be authorized.
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        boolean scramOnly = JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly");
        return !scramOnly;
    }

    public String getPassword(String username) throws UserNotFoundException {
        if (!supportsPasswordRetrieval()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain.
                throw new UserNotFoundException();
            }
        }
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PASSWORD);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException(username);
            }
            String plainText = rs.getString(1);
            String encrypted = rs.getString(2);
            if (encrypted != null) {
                try {
                    return AuthFactory.decryptPassword(encrypted);
                }
                catch (UnsupportedOperationException uoe) {
                    // Ignore and return plain password instead.
                }
            }
            if (plainText == null) {
                throw new UnsupportedOperationException();
            }
            return plainText;
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    public boolean checkPassword(String username, String testPassword) throws UserNotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain.
                throw new UserNotFoundException();
            }
        }
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(TEST_PASSWORD);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException(username);
            }
            String plainText = rs.getString(1);
            String encrypted = rs.getString(2);
            int iterations = rs.getInt(3);
            String salt = rs.getString(4);
            String storedKey = rs.getString(5);
            if (encrypted != null) {
                try {
                    plainText = AuthFactory.decryptPassword(encrypted);
                }
                catch (UnsupportedOperationException uoe) {
                    // Ignore and return plain password instead.
                }
            }
            if (plainText != null) {
                boolean scramOnly = JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly");
                if (scramOnly) {
                    // If we have a password here, but we're meant to be scramOnly, we should reset it.
                    setPassword(username, plainText);
                }
                return testPassword.equals(plainText);
            }
            // Don't have either plain or encrypted, so test SCRAM hash.
            if (salt == null || iterations == 0 || storedKey == null) {
                Log.warn("No available credentials for checkPassword.");
                return false;
            }
            byte[] saltShaker = DatatypeConverter.parseBase64Binary(salt);
            byte[] saltedPassword = null, clientKey = null, testStoredKey = null;
            try {
                   saltedPassword = ScramUtils.createSaltedPassword(saltShaker, testPassword, iterations);
                   clientKey = ScramUtils.computeHmac(saltedPassword, "Client Key");
                   testStoredKey = MessageDigest.getInstance("SHA-1").digest(clientKey);
            } catch(SaslException | NoSuchAlgorithmException | UnsupportedEncodingException e) {
                Log.warn("Unable to check SCRAM values for PLAIN authentication.");
                return false;
            }
            return DatatypeConverter.printBase64Binary(testStoredKey).equals(storedKey);
        }
        catch (SQLException sqle) {
            Log.error("User SQL failure:", sqle);
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    public void setPassword(String username, String password) throws UserNotFoundException {
        // Determine if the password should be stored as plain text or encrypted.
        boolean usePlainPassword = JiveGlobals.getBooleanProperty("user.usePlainPassword");
        boolean scramOnly = JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly");
        String encryptedPassword = null;
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain.
                throw new UserNotFoundException();
            }
        }
        
        // Store the salt and salted password so SCRAM-SHA-1 SASL auth can be used later.
        byte[] saltShaker = new byte[32];
        random.nextBytes(saltShaker);
        String salt = DatatypeConverter.printBase64Binary(saltShaker);

        
        int iterations = JiveGlobals.getIntProperty("sasl.scram-sha-1.iteration-count",
                        ScramUtils.DEFAULT_ITERATION_COUNT);
        byte[] saltedPassword = null, clientKey = null, storedKey = null, serverKey = null;
	try {
	       saltedPassword = ScramUtils.createSaltedPassword(saltShaker, password, iterations);
               clientKey = ScramUtils.computeHmac(saltedPassword, "Client Key");
               storedKey = MessageDigest.getInstance("SHA-1").digest(clientKey);
               serverKey = ScramUtils.computeHmac(saltedPassword, "Server Key");
       } catch (SaslException | NoSuchAlgorithmException | UnsupportedEncodingException e) {
           Log.warn("Unable to persist values for SCRAM authentication.");
       }
   
        if (!scramOnly && !usePlainPassword) {
            try {
                encryptedPassword = AuthFactory.encryptPassword(password);
                // Set password to null so that it's inserted that way.
                password = null;
            }
            catch (UnsupportedOperationException uoe) {
                // Encryption may fail. In that case, ignore the error and
                // the plain password will be stored.
            }
        }
        if (scramOnly) {
            encryptedPassword = null;
            password = null;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PASSWORD);
            if (password == null) {
                pstmt.setNull(1, Types.VARCHAR);
            }
            else {
                pstmt.setString(1, password);
            }
            if (encryptedPassword == null) {
                pstmt.setNull(2, Types.VARCHAR);
            }
            else {
                pstmt.setString(2, encryptedPassword);
            }
            if (storedKey == null) {
            	pstmt.setNull(3, Types.VARCHAR);
            }
            else {
            	pstmt.setString(3, DatatypeConverter.printBase64Binary(storedKey));
            }
            if (serverKey == null) {
            	pstmt.setNull(4, Types.VARCHAR);
            }
            else {
            	pstmt.setString(4, DatatypeConverter.printBase64Binary(serverKey));
            }
            pstmt.setString(5, salt);
            pstmt.setInt(6, iterations);
            pstmt.setString(7, username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public boolean supportsPasswordRetrieval() {
        boolean scramOnly = JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly");
        return !scramOnly;
    }

    @Override
    public boolean isScramSupported() {
        return true;
    }
}