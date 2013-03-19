/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.qq;

import net.sf.jqql.QQ;
import net.sf.kraken.BaseTransport;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.PresenceType;
import net.sf.kraken.type.TransportLoginStatus;

import org.jivesoftware.util.LocaleUtils;
import org.xmpp.packet.JID;


/**
 * QQ Transport Interface.
 *
 * This handles the bulk of the QQ work via BaseTransport and provides
 * some gateway specific interactions.
 *
 * @author lizongbo
 */
public class QQTransport extends BaseTransport<QQBuddy> {
    /**
     * @see net.sf.kraken.BaseTransport#getTerminologyUsername()
     */
    @Override
    public String getTerminologyUsername() {
        return LocaleUtils.getLocalizedString("gateway.qq.username", "kraken");
    }

    /**
     * @see net.sf.kraken.BaseTransport#getTerminologyPassword()
     */
    @Override
    public String getTerminologyPassword() {
        return LocaleUtils.getLocalizedString("gateway.qq.password", "kraken");
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
        return LocaleUtils.getLocalizedString("gateway.qq.registration", "kraken");
    }

    /**
     * @see net.sf.kraken.BaseTransport#isPasswordRequired()
     */
    @Override
    public Boolean isPasswordRequired() {
        return true;
    }

    /**
     * @see net.sf.kraken.BaseTransport#isNicknameRequired()
     */
    @Override
    public Boolean isNicknameRequired() {
        return false;
    }

    /**
     * @see net.sf.kraken.BaseTransport#isUsernameValid(String)
     */
    @Override
    public Boolean isUsernameValid(String username) {
        try {
            Integer.parseInt(username);
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    /**
     * Handles creating a QQ session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    @Override
    public TransportSession<QQBuddy> registrationLoggedIn(Registration registration,
                                                 JID jid,
                                                 PresenceType presenceType,
                                                 String verboseStatus,
                                                 Integer priority) {
        TransportSession<QQBuddy> session = new QQSession(registration, jid, this,
                                                 priority);
        session.setLoginStatus(TransportLoginStatus.LOGGING_IN);
        session.logIn(presenceType, verboseStatus);
        return session;
    }

    /**
     * Handles logging out of a QQ session.
     *
     * @param session The session to be disconnected.
     */
    @Override
    public void registrationLoggedOut(TransportSession<QQBuddy> session) {
        session.setLoginStatus(TransportLoginStatus.LOGGING_OUT);
        session.logOut();
    }

    /**
     * Converts a jabber status to an QQ status.
     *
     * @param jabStatus Jabber presence type.
     * @return QQ user status id.
     */
    public byte convertJabStatusToQQ(PresenceType jabStatus) {
        if (jabStatus == PresenceType.available) {
            return QQ.QQ_STATUS_ONLINE;
        } else if (jabStatus == PresenceType.away) {
            return QQ.QQ_STATUS_AWAY;
        } else if (jabStatus == PresenceType.xa) {
            return QQ.QQ_STATUS_AWAY;
        } else if (jabStatus == PresenceType.dnd) {
            return QQ.QQ_STATUS_AWAY;
        } else if (jabStatus == PresenceType.chat) {
            return QQ.QQ_STATUS_ONLINE;
        } else if (jabStatus == PresenceType.unavailable) {
            return QQ.QQ_STATUS_OFFLINE;
        } else {
            return QQ.QQ_STATUS_ONLINE;
        }
    }

    /**
     * Converts a QQ status to an XMPP status.
     *
     * @param qqStatus QQ status constant.
     * @return XMPP presence type matching the QQ status.
     */
    public PresenceType convertQQStatusToXMPP(byte qqStatus) {
        switch (qqStatus) {
	        case QQ.QQ_STATUS_AWAY:
	            return PresenceType.away;
	        case QQ.QQ_STATUS_HIDDEN:
	            return PresenceType.xa;
	        case QQ.QQ_STATUS_OFFLINE:
	            return PresenceType.unavailable;
	        case QQ.QQ_STATUS_ONLINE:
	            return PresenceType.available;
	        default:
	            return PresenceType.unknown;
        }
    }

}
