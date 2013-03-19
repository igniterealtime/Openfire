/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.xmpp.packet;

import org.jivesoftware.smack.packet.PacketExtension;

/**
 * @author Daniel Henninger
 */
public class GoogleUserSettingExtension implements PacketExtension {

    public GoogleUserSettingExtension() {
        
    }

    public GoogleUserSettingExtension(Boolean autoAcceptSuggestions, Boolean mailNotifications, Boolean archivingEnabled) {
        this.autoAcceptSuggestions = autoAcceptSuggestions;
        this.mailNotifications = mailNotifications;
        this.archivingEnabled = archivingEnabled;
    }

    public static String ELEMENT_NAME = "usersetting";
    public static String NAMESPACE = "google:setting";

    public String getElementName() {
        return ELEMENT_NAME;
    }

    public String getNamespace() {
        return NAMESPACE;
    }

    private Boolean autoAcceptSuggestions = null;
    private Boolean mailNotifications = null;
    private Boolean archivingEnabled = null;

    public Boolean getAutoAcceptSuggestions() {
        return autoAcceptSuggestions;
    }

    public void setAutoAcceptSuggestions(Boolean autoAcceptSuggestions) {
        this.autoAcceptSuggestions = autoAcceptSuggestions;
    }

    public Boolean getMailNotifications() {
        return mailNotifications;
    }

    public void setMailNotifications(Boolean mailNotifications) {
        this.mailNotifications = mailNotifications;
    }

    public Boolean getArchivingEnabled() {
        return archivingEnabled;
    }

    public void setArchivingEnabled(Boolean archivingEnabled) {
        this.archivingEnabled = archivingEnabled;
    }

    public String toXML() {
        StringBuffer buf = new StringBuffer();
        buf.append("<").append(getElementName()).append(" xmlns=\"").append(getNamespace()).append("\">");
        if (autoAcceptSuggestions != null) {
            buf.append("<autoacceptsuggestions value=\"").append(autoAcceptSuggestions ? "true" : "false").append("\"/>");
        }
        if (mailNotifications != null) {
            buf.append("<mailnotifications value=\"").append(mailNotifications ? "true" : "false").append("\"/>");
        }
        if (archivingEnabled != null) {
            buf.append("<archivingenabled value=\"").append(archivingEnabled ? "true" : "false").append("\"/>");
        }
        buf.append("</").append(getElementName()).append(">");
        return buf.toString();
    }

}
