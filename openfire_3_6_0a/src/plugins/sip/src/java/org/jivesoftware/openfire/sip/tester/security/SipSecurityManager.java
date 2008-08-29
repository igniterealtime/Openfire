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

package org.jivesoftware.openfire.sip.tester.security;

import org.jivesoftware.openfire.sip.tester.Log;
import org.jivesoftware.openfire.sip.tester.stack.SIPConfig;
import org.jivesoftware.openfire.sip.tester.stack.SipManager;

import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ListIterator;

/**
 * <p/>
 * Title: Netsite TudoMais
 * </p>
 * <p/>
 * Description:JAIN-SIP Audio/Video phone application
 * </p>
 * <p/>
 * Copyright: Copyright (c) 2006
 * </p>
 * <p/>
 * Organisation: CTBC Telecom / Netsite
 * </p>
 *
 * @author Thiago Rocha Camargo (thiago@jivesoftware.com)
 */

public class SipSecurityManager {
    /**
     */
    private HeaderFactory headerFactory = null;

    /**
     */
    private SipProvider transactionCreator = null;

    private SipManager sipManCallback = null;

    /**
     * Credentials cached so far.
     */
    CredentialsCache cachedCredentials = new CredentialsCache();

    public UserCredentials defaultCredentials = null;

    public SipSecurityManager() {

    }

    public UserCredentials getDefaultCredentials() {
        return defaultCredentials;
    }

    public void setDefaultCredentials(UserCredentials defaultCredentials) {
        this.defaultCredentials = defaultCredentials;
    }

    /**
     * set the header factory to be used when creating authorization headers
     *
     * @uml.property name="headerFactory"
     */
    public void setHeaderFactory(HeaderFactory headerFactory) {
        this.headerFactory = headerFactory;
    }

    /**
     * Verifies whether there are any user credentials registered for the call
     * that "request" belongs to and appends corresponding authorization headers
     * if that is the case.
     *
     * @param request the request that needs to be attached credentials.
     */
    public void appendCredentialsIfNecessary(Request request) {
        // TODO IMPLEMENT
    }

    /**
     * Uses securityAuthority to determinie a set of valid user credentials for
     * the specified Response (Challenge) and appends it to the challenged
     * request so that it could be retransmitted.
     * <p/>
     * Fredrik Wickstrom reported that dialog cseq counters are not incremented
     * when resending requests. He later uncovered additional problems and
     * proposed a way to fix them (his proposition was taken into account).
     *
     * @param challenge             the 401/407 challenge response
     * @param challengedTransaction the transaction established by the challenged request
     * @return a transaction containing a reoriginated request with the
     *         necessary authorization header.
     * @throws SipSecurityException
     */
    public ClientTransaction handleChallenge(Response challenge,
                                             ClientTransaction challengedTransaction)
            throws SipSecurityException, SipException,
            InvalidArgumentException, ParseException {
        try {

            String branchID = challengedTransaction.getBranchId();
            Request challengedRequest = challengedTransaction.getRequest();

            Request reoriginatedRequest = (Request) challengedRequest.clone();

            ListIterator authHeaders = null;

            if (challenge == null || reoriginatedRequest == null)
                throw new NullPointerException(
                        "A null argument was passed to handle challenge.");

            // CallIdHeader callId =
            // (CallIdHeader)challenge.getHeader(CallIdHeader.NAME);

            if (challenge.getStatusCode() == Response.UNAUTHORIZED)
                authHeaders = challenge.getHeaders(WWWAuthenticateHeader.NAME);
            else if (challenge.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED)
                authHeaders = challenge
                        .getHeaders(ProxyAuthenticateHeader.NAME);

            if (authHeaders == null)
                throw new SecurityException(
                        "Could not find WWWAuthenticate or ProxyAuthenticate headers");

            // Remove all authorization headers from the request (we'll re-add
            // them
            // from cache)
            reoriginatedRequest.removeHeader(AuthorizationHeader.NAME);
            reoriginatedRequest.removeHeader(ProxyAuthorizationHeader.NAME);

            // rfc 3261 says that the cseq header should be augmented for the
            // new
            // request. do it here so that the new dialog (created together with
            // the new client transaction) takes it into account.
            // Bug report - Fredrik Wickstrom
            CSeqHeader cSeq = (CSeqHeader) reoriginatedRequest
                    .getHeader((CSeqHeader.NAME));
            cSeq.setSequenceNumber(cSeq.getSequenceNumber() + 1);

            ClientTransaction retryTran = transactionCreator
                    .getNewClientTransaction(reoriginatedRequest);

            WWWAuthenticateHeader authHeader = null;
            CredentialsCacheEntry ccEntry = null;
            while (authHeaders.hasNext()) {
                authHeader = (WWWAuthenticateHeader) authHeaders.next();
                String realm = authHeader.getRealm();

                // Check whether we have cached credentials for authHeader's
                // realm
                // make sure that if such credentials exist they get removed.
                // The
                // challenge means that there's something wrong with them.
                ccEntry = cachedCredentials.remove(realm);

                // Try to guess user name and facilitate user
                UserCredentials defaultCredentials = new UserCredentials();
                FromHeader from = (FromHeader) reoriginatedRequest
                        .getHeader(FromHeader.NAME);
                URI uri = from.getAddress().getURI();
                if (uri.isSipURI()) {
                    Log.debug("handleChallenge", SIPConfig.getAuthUserName());
                    String user = SIPConfig.getAuthUserName() != null ? SIPConfig
                            .getAuthUserName()
                            : ((SipURI) uri).getUser();
                    defaultCredentials.setAuthUserName(user == null ? SIPConfig
                            .getUserName() : user);
                }

                boolean ccEntryHasSeenTran = false;

                if (ccEntry != null)
                    ccEntryHasSeenTran = ccEntry.processResponse(branchID);

                // get a new pass
                if (ccEntry == null // we don't have credentials for the
                        // specified realm
                        || ((!authHeader.isStale() && ccEntryHasSeenTran))) {
                    if (ccEntry == null) {
                        ccEntry = new CredentialsCacheEntry();

                        ccEntry.userCredentials = defaultCredentials;

                    }
                    // put the returned user name in the properties file
                    // so that it appears as a default one next time user is
                    // prompted for pass
                    SIPConfig
                            .setUserName(ccEntry.userCredentials.getUserName());
                }
                // encode and send what we have
                else if (ccEntry != null
                        && (!ccEntryHasSeenTran || authHeader.isStale())) {
                }

                // if user canceled or sth else went wrong
                if (ccEntry.userCredentials == null)
                    throw new SecurityException(
                            "Unable to authenticate with realm " + realm);

                AuthorizationHeader authorization = this.getAuthorization(
                        reoriginatedRequest.getMethod(), reoriginatedRequest
                        .getRequestURI().toString(),
                        reoriginatedRequest.getContent() == null ? ""
                                : reoriginatedRequest.getContent().toString(),
                        authHeader, ccEntry.userCredentials);

                ccEntry.processRequest(retryTran.getBranchId());
                cachedCredentials.cacheEntry(realm, ccEntry);

                reoriginatedRequest.addHeader(authorization);

                // if there was trouble with the user - make sure we fix it
                if (uri.isSipURI()) {
                    ((SipURI) uri).setUser(ccEntry.userCredentials
                            .getUserName());
                    Address add = from.getAddress();
                    add.setURI(uri);
                    from.setAddress(add);
                    reoriginatedRequest.setHeader(from);
                    if (challengedRequest.getMethod().equals(Request.REGISTER)) {
                        ToHeader to = (ToHeader) reoriginatedRequest
                                .getHeader(ToHeader.NAME);
                        add.setURI(uri);
                        to.setAddress(add);
                        reoriginatedRequest.setHeader(to);

                    }

                    // very ugly but very necessary

                    sipManCallback.setCurrentlyUsedURI(uri.toString());
                    Log.debug("URI: " + uri.toString());

                }

                // if this is a register - fix to as well

            }

            return retryTran;
        }
        catch (Exception e) {
            Log.debug("ERRO REG: " + e.toString());
            return null;
        }

    }

    /**
     * Generates an authorisation header in response to wwwAuthHeader.
     *
     * @param method          method of the request being authenticated
     * @param uri             digest-uri
     * @param authHeader      the challenge that we should respond to
     * @param userCredentials username and pass
     * @return an authorisation header in response to wwwAuthHeader.
     */
    private AuthorizationHeader getAuthorization(String method, String uri,
                                                 String requestBody, WWWAuthenticateHeader authHeader,
                                                 UserCredentials userCredentials) throws SecurityException {
        String response = null;
        try {
            Log.debug("getAuthorization", userCredentials.getAuthUserName());
            response = MessageDigestAlgorithm.calculateResponse(authHeader
                    .getAlgorithm(), userCredentials.getAuthUserName(),
                    authHeader.getRealm(), new String(userCredentials
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

            authorization.setUsername(userCredentials.getAuthUserName());
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
            throw new SecurityException(
                    "Failed to create an authorization header!");
        }

        return authorization;
    }

    public void cacheCredentials(String realm, UserCredentials credentials) {
        CredentialsCacheEntry ccEntry = new CredentialsCacheEntry();
        ccEntry.userCredentials = credentials;

        this.cachedCredentials.cacheEntry(realm, ccEntry);
    }

    /**
     * Sets a valid SipProvider that would enable the security manager to map
     * credentials to transactionsand thus understand when it is suitable to use
     * cached passwords and when it should go ask the user.
     *
     * @param transactionCreator a valid SipProvder instance
     * @uml.property name="transactionCreator"
     */
    public void setTransactionCreator(SipProvider transactionCreator) {
        this.transactionCreator = transactionCreator;
    }

    /**
     * If the user name was wrong and the user fixes it here we should als
     * notify the sip manager that the currentlyUsedURI it has is not valid.
     *
     * @param sipManCallback a valid instance of SipMaqnager
     * @uml.property name="sipManCallback"
     */
    public void setSipManCallback(SipManager sipManCallback) {
        this.sipManCallback = sipManCallback;
    }

}
