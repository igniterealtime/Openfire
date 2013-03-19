/**
 * $RCSfile$
 * $Revision: 19036 $
 * $Date: 2005-06-13 16:53:54 -0700 (Mon, 13 Jun 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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
