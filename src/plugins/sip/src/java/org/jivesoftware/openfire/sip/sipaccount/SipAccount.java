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

package org.jivesoftware.openfire.sip.sipaccount;

import org.jivesoftware.util.JiveGlobals;

/**
 * SipAccount instance. This class handle all SIP account information for a user
 *
 * @author Thiago Rocha Camargo
 */
public class SipAccount {

    private String username = null;

    private String sipUsername = "";

    private String authUsername = "";

    private String displayName = "";

    private String password = "";

    private String server = "";

    private String outboundproxy = "";

    private String stunServer = "";

    private String stunPort = "";

    private String voiceMailNumber = "";

    private boolean useStun = false;

    private boolean enabled = false;

    private boolean promptCredentials = false;

    private SipRegisterStatus status = SipRegisterStatus.Unregistered;

    public SipAccount(String username, String sipUsername, String authUsername, String displayName, String password, String server, String outboundproxy, boolean promptCredentials) {
        this.username = username;
        this.sipUsername = sipUsername;
        this.authUsername = authUsername;
        this.displayName = displayName;
        this.password = password;
        this.server = server;
        this.outboundproxy = outboundproxy;
        this.promptCredentials = promptCredentials;
    }

    public SipAccount(String username) {
        this.username = username;
    }

    public String getAuthUsername() {
        return authUsername == null ? "" : authUsername;
    }

    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    public String getDisplayName() {
        return displayName == null ? "" : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getVoiceMailNumber() {
        return voiceMailNumber == null ? JiveGlobals.getProperty("phone.voiceMail", "") : voiceMailNumber;
    }

    public void setVoiceMailNumber(String voiceMailNumber) {
        this.voiceMailNumber = voiceMailNumber;
    }

    public String getServer() {
        return server == null ? JiveGlobals.getProperty("phone.sipServer", "") : server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getOutboundproxy() {
        return outboundproxy;
    }

    public void setOutboundproxy(String outboundproxy) {
        this.outboundproxy = outboundproxy;
    }

    public String getSipUsername() {
        return sipUsername == null ? "" : sipUsername;
    }

    public void setSipUsername(String sipUsername) {
        this.sipUsername = sipUsername;
    }

    public String getUsername() {
        return username == null ? "" : username;
    }

    public String getPassword() {
        return password == null ? "" : password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStunPort() {
        return stunPort == null ? JiveGlobals.getProperty("phone.stunPort", "") : stunPort;
    }

    public void setStunPort(String stunPort) {
        this.stunPort = stunPort;
    }

    public String getStunServer() {
        return stunServer == null ? JiveGlobals.getProperty("phone.stunServer", "") : stunServer;
    }

    public void setStunServer(String stunServer) {
        this.stunServer = stunServer;
    }

    public boolean isUseStun() {
        if (stunPort == null && stunServer == null) {
            return JiveGlobals.getBooleanProperty("phone.stunEnabled", false);
        }
        return useStun;
    }

    public void setUseStun(boolean useStun) {
        this.useStun = useStun;
    }

    public SipRegisterStatus getStatus() {
        return status == null ? SipRegisterStatus.Unregistered : status;
    }

    public void setStatus(SipRegisterStatus status) {
        this.status = status;
    }

    public boolean isPromptCredentials() {
        return promptCredentials;
    }

    public void setPromptCredentials(boolean promptCredentials) {
        this.promptCredentials = promptCredentials;
    }
}
