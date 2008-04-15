/**
 * $RCSfile$
 * $Revision: 19036 $
 * $Date: 2005-06-13 16:53:54 -0700 (Mon, 13 Jun 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.settings.offline;

import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;

public class OfflineSettings {
    private String redirectURL;

    private String offlineText;
    private String emailAddress;
    private String subject;

    public String getRedirectURL() {
        if(!ModelUtil.hasLength(redirectURL)){
            return "";
        }
        return redirectURL;
    }

    public void setRedirectURL(String redirectURL) {
        this.redirectURL = redirectURL;
    }

    public String getOfflineText() {
        if(!ModelUtil.hasLength(offlineText)){
            return "";
        }
        return offlineText;
    }

    public void setOfflineText(String offlineText) {
        this.offlineText = offlineText;
    }

    public String getEmailAddress() {
        if(!ModelUtil.hasLength(emailAddress)){
            return "";
        }
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getSubject() {
        if(!ModelUtil.hasLength(subject)){
            return "";
        }
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public boolean redirects(){
        return (ModelUtil.hasLength(getRedirectURL()));
    }
}
