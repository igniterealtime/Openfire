/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.oscar;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.wildfire.gateway.*;
import org.xmpp.packet.JID;
import org.dom4j.Element;
import org.dom4j.DocumentHelper;

/**
 * OSCAR Transport Interface.
 *
 * This handles the bulk of the XMPP work via BaseTransport and provides
 * some gateway specific interactions.
 *
 * @author Daniel Henninger
 */
public class OSCARTransport extends BaseTransport {

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyUsername()
     */
    public String getTerminologyUsername() {
        return LocaleUtils.getLocalizedString("gateway."+getType().toString()+".username", "gateway");
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyPassword()
     */
    public String getTerminologyPassword() {
        return LocaleUtils.getLocalizedString("gateway."+getType().toString()+".password", "gateway");
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyNickname()
     */
    public String getTerminologyNickname() {
        return null;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyRegistration()
     */
    public String getTerminologyRegistration() {
        return LocaleUtils.getLocalizedString("gateway."+getType().toString()+".registration", "gateway");
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#isPasswordRequired()
     */
    public Boolean isPasswordRequired() { return true; }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#isNicknameRequired()
     */
    public Boolean isNicknameRequired() { return false; }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#isUsernameValid(String)
     */
    public Boolean isUsernameValid(String username) {
        if (getType() == TransportType.icq) {
            return username.matches("\\d+");
        }
        else {
            return username.matches("\\w+") || username.matches("\\w+@[\\w\\.]+");
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getOptionsConfig(org.jivesoftware.wildfire.gateway.TransportType)
     * @param type The transport type to distinguish if needed.
     * @return XML document describing the options interface.
     */
    public static Element getOptionsConfig(TransportType type) {
        Element optConfig = DocumentHelper.createElement("optionconfig");
        Element leftPanel = optConfig.addElement("leftpanel");
        Element rightPanel = optConfig.addElement("rightpanel");
        rightPanel.addElement("item")
                  .addAttribute("type", "text")
                  .addAttribute("sysprop", "plugin.gateway."+type.toString()+".connecthost")
                  .addAttribute("desc", "Host");
        rightPanel.addElement("item")
                  .addAttribute("type", "text")
                  .addAttribute("sysprop", "plugin.gateway."+type.toString()+".connectport")
                  .addAttribute("desc", "Port");
        if (type == TransportType.icq) {
            rightPanel.addElement("item")
                      .addAttribute("type", "text")
                      .addAttribute("sysprop", "plugin.gateway.icq.encoding")
                      .addAttribute("desc", "Encoding");
        }
        return optConfig;
    }

    /**
     * Handles creating an OSCAR session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    public TransportSession registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority) {
        TransportSession session = new OSCARSession(registration, jid, this, priority);
        this.getSessionManager().startThread(session);
        ((OSCARSession)session).logIn(presenceType, verboseStatus);
        return session;
    }

    /**
     * Handles logging out of a Yahoo session.
     *
     * @param session The session to be disconnected.
     */
    public void registrationLoggedOut(TransportSession session) {
        ((OSCARSession)session).logOut();
        session.sessionDone();
        // Just in case.
        session.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
    }

}
