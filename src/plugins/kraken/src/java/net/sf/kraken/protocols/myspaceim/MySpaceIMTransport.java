/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.myspaceim;

import net.sf.jmyspaceiml.packet.StatusMessage;
import net.sf.kraken.BaseTransport;
import net.sf.kraken.KrakenPlugin;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.PresenceType;
import net.sf.kraken.type.TransportLoginStatus;

import org.jivesoftware.util.LocaleUtils;
import org.xmpp.packet.JID;

/**
 * MySpaceIM Transport Interface.
 *
 * This handles the bulk of the XMPP work via BaseTransport and provides
 * some gateway specific interactions.
 *
 * @author Daniel Henninger
 */
public class MySpaceIMTransport extends BaseTransport<MySpaceIMBuddy> {

    /**
     * @see net.sf.kraken.BaseTransport#getTerminologyUsername()
     */
    @Override
    public String getTerminologyUsername() {
        return LocaleUtils.getLocalizedString("gateway.myspaceim.username", "kraken");
    }

    /**
     * @see net.sf.kraken.BaseTransport#getTerminologyPassword()
     */
    @Override
    public String getTerminologyPassword() {
        return LocaleUtils.getLocalizedString("gateway.myspaceim.password", "kraken");
    }

    /**
     * @see net.sf.kraken.BaseTransport#getTerminologyNickname()
     */
    @Override
    public String getTerminologyNickname() {
        return null;
    }

    /**
     * @see net.sf.kraken.BaseTransport#getTerminologyRegistration()
     */
    @Override
    public String getTerminologyRegistration() {
        return LocaleUtils.getLocalizedString("gateway.myspaceim.registration", "kraken");
    }

    /**
     * @see net.sf.kraken.BaseTransport#isPasswordRequired()
     */
    @Override
    public Boolean isPasswordRequired() { return true; }

    /**
     * @see net.sf.kraken.BaseTransport#isNicknameRequired()
     */
    @Override
    public Boolean isNicknameRequired() { return false; }

    /**
     * @see net.sf.kraken.BaseTransport#isUsernameValid(String)
     */
    @Override
    public Boolean isUsernameValid(String username) {
        return true;
    }

    /**
     * Handles creating a MySpaceIM session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    @Override
    public TransportSession<MySpaceIMBuddy> registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority) {
        TransportSession<MySpaceIMBuddy> session = new MySpaceIMSession(registration, jid, this, priority);
        session.setLoginStatus(TransportLoginStatus.LOGGING_IN);
        session.logIn(presenceType, verboseStatus);
        return session;
    }

    /**
     * Handles logging out of a MySpaceIM session.
     *
     * @param session The session to be disconnected.
     */
    @Override
    public void registrationLoggedOut(TransportSession<MySpaceIMBuddy> session) {
        session.setLoginStatus(TransportLoginStatus.LOGGING_OUT);
        session.logOut();
    }
    
    /**
     * Converts a XMPP status to a MySpaceIM status.
     *
     * @param jabStatus XMPP presence type.
     * @return MySpaceIM user status type.
     */
    public int convertXMPPStatusToMySpaceIM(PresenceType jabStatus) {
        if (jabStatus == PresenceType.available) {
            return StatusMessage.MSIM_STATUS_CODE_ONLINE;
        }
        else if (jabStatus == PresenceType.away) {
            return StatusMessage.MSIM_STATUS_CODE_AWAY;
        }
        else if (jabStatus == PresenceType.xa) {
            return StatusMessage.MSIM_STATUS_CODE_AWAY;
        }
        else if (jabStatus == PresenceType.dnd) {
            return StatusMessage.MSIM_STATUS_CODE_AWAY;
        }
        else if (jabStatus == PresenceType.chat) {
            return StatusMessage.MSIM_STATUS_CODE_ONLINE;
        }
        else if (jabStatus == PresenceType.unavailable) {
            return StatusMessage.MSIM_STATUS_CODE_OFFLINE;
        }
        else {
            return StatusMessage.MSIM_STATUS_CODE_ONLINE;
        }
    }

    /**
     * Converts a MySpaceIM status to an XMPP status.
     *
     * @param msUserStatus MySpaceIM user status constant.
     * @return XMPP presence type.
     */
    public PresenceType convertMySpaceIMStatusToXMPP(int msUserStatus) {
        switch (msUserStatus) {
            case StatusMessage.MSIM_STATUS_CODE_OFFLINE:
                return PresenceType.unavailable;
            case StatusMessage.MSIM_STATUS_CODE_IDLE:
                return PresenceType.away;
            case StatusMessage.MSIM_STATUS_CODE_AWAY:
                return PresenceType.away;
            case StatusMessage.MSIM_STATUS_CODE_ONLINE:
                return PresenceType.available;
            default:
                return PresenceType.unknown;
        }
     }
    
    static {
        KrakenPlugin.setLoggerProperty("log4j.additivity.net.sf.jmyspaceiml", "false");
        KrakenPlugin.setLoggerProperty("log4j.logger.net.sf.jmyspaceiml", "DEBUG, openfiredebug");
    }

}
