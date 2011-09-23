/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.yahoo;

import net.sf.kraken.BaseTransport;
import net.sf.kraken.KrakenPlugin;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.PresenceType;
import net.sf.kraken.type.TransportLoginStatus;

import org.apache.log4j.Logger;
import org.jivesoftware.util.LocaleUtils;
import org.openymsg.network.Status;
import org.xmpp.packet.JID;

/**
 * Yahoo Transport Interface.
 *
 * This handles the bulk of the XMPP work via BaseTransport and provides
 * some gateway specific interactions.
 *
 * @author Daniel Henninger
 */
public class YahooTransport extends BaseTransport<YahooBuddy> {

    static Logger Log = Logger.getLogger(YahooTransport.class);

    /**
     * @see net.sf.kraken.BaseTransport#getTerminologyUsername()
     */
    @Override
    public String getTerminologyUsername() {
        return LocaleUtils.getLocalizedString("gateway.yahoo.username", "kraken");
    }

    /**
     * @see net.sf.kraken.BaseTransport#getTerminologyPassword()
     */
    @Override
    public String getTerminologyPassword() {
        return LocaleUtils.getLocalizedString("gateway.yahoo.password", "kraken");
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
        return LocaleUtils.getLocalizedString("gateway.yahoo.registration", "kraken");
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
        return username.matches("[^ \\p{Cntrl}()@,;:\\\\\"\\[\\]]+@?[^ \\p{Cntrl}()@,;:\\\\\"\\[\\]]+");
    }

    /**
     * Handles creating a Yahoo session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    @Override
    public TransportSession<YahooBuddy> registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority) {
        TransportSession<YahooBuddy> session = new YahooSession(registration, jid, this, priority);
        session.setLoginStatus(TransportLoginStatus.LOGGING_IN);
        session.logIn(presenceType, verboseStatus);
        return session;
    }

    /**
     * Handles logging out of a Yahoo session.
     *
     * @param session The session to be disconnected.
     */
    @Override
    public void registrationLoggedOut(TransportSession<YahooBuddy> session) {
        session.setLoginStatus(TransportLoginStatus.LOGGING_OUT);
        session.logOut();
    }

    /**
     * Converts an XMPP status to an Yahoo status.
     *
     * @param xmppStatus Jabber presence type.
     * @return Yahoo status identifier.
     */
    public Status convertXMPPStatusToYahoo(PresenceType xmppStatus) {
        if (xmppStatus == PresenceType.available) {
            return Status.AVAILABLE;
        }
        else if (xmppStatus == PresenceType.away) {
            return Status.BRB;
        }
        else if (xmppStatus == PresenceType.xa) {
            return Status.STEPPEDOUT;
        }
        else if (xmppStatus == PresenceType.dnd) {
            return Status.BUSY;
        }
        else if (xmppStatus == PresenceType.chat) {
            return Status.AVAILABLE;
        }
        else if (xmppStatus == PresenceType.unavailable) {
            return Status.OFFLINE;
        }
        else {
            return Status.AVAILABLE;
        }
    }

    /**
     * Converts a Yahoo status to an XMPP status.
     *
     * @param yahooStatus Yahoo StatusConstants constant.
     * @return XMPP presence type matching the Yahoo status.
     */
    public PresenceType convertYahooStatusToXMPP(Status yahooStatus, String customAvailable) {
    	if ("0".equals(customAvailable)) {
            return PresenceType.available;
    	} else if ("1".equals(customAvailable)) {
    		return PresenceType.dnd;
    	} 
    	else if (yahooStatus == Status.AVAILABLE) {
            // We're good, leave the type as blank for available.
            return PresenceType.available;
        }
        else if (yahooStatus == Status.BRB) {
            return PresenceType.away;
        }
        else if (yahooStatus == Status.BUSY) {
            return PresenceType.dnd;
        }
        else if (yahooStatus == Status.IDLE) {
            return PresenceType.away;
        }
        else if (yahooStatus == Status.OFFLINE) {
            return PresenceType.unavailable;
        }
        else if (yahooStatus == Status.NOTATDESK) {
            return PresenceType.away;
        }
        else if (yahooStatus == Status.NOTINOFFICE) {
            return PresenceType.away;
        }
        else if (yahooStatus == Status.ONPHONE) {
            return PresenceType.away;
        }
        else if (yahooStatus == Status.ONVACATION) {
            return PresenceType.xa;
        }
        else if (yahooStatus == Status.OUTTOLUNCH) {
            return PresenceType.xa;
        }
        else if (yahooStatus == Status.STEPPEDOUT) {
            return PresenceType.away;
        }
        else if (yahooStatus == Status.INVISIBLE) {
            return PresenceType.available;
        }
        else if (yahooStatus == Status.CUSTOM) {
            return PresenceType.available;
        }
        else {
            // Not something we handle, we're going to ignore it.
            Log.warn("Yahoo: Unrecognized status "+yahooStatus+" received.");
            return PresenceType.unknown;
        }
    }

    static {
        KrakenPlugin.setLoggerProperty("log4j.additivity.org.openymsg", "false");
        KrakenPlugin.setLoggerProperty("log4j.logger.org.openymsg", "DEBUG, openfiredebug");
    }

}
