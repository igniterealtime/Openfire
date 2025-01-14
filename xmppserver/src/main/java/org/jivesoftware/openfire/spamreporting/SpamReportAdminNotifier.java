/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.spamreporting;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.SystemProperty;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * An event listener that will send an XMPP message to the configured administrators of the server when a spam report
 * has been filed.
 *
 * @author Guus der Kinderen, guus.der.kinderen@mgail.com
 */
public class SpamReportAdminNotifier implements SpamReportEventListener
{
    /**
     * Defines if notifications (via XMPP) are to be sent to administrators of the server when a spam report is received.
     */
    private static final SystemProperty<Boolean> NOTIFY_ADMINS_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("spamreport.notify-admins.enabled")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();


    @Override
    public void receivedSpamReport(@Nonnull final SpamReport spamReport)
    {
        if (!NOTIFY_ADMINS_ENABLED.getValue()) {
            return;
        }

        XMPPServer.getInstance().sendMessageToAdmins(
            LocaleUtils.getLocalizedString("spamreport.notify-admins.notification-message", List.of(spamReport.getReportingAddress(), spamReport.getReportedAddress()))
        );
    }
}
