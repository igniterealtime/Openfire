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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * A manager object of processes related to spam rapports.
 *
 * @author Guus der Kinderen, guus.der.kinderen@mgail.com
 */
public class SpamReportManager
{
    private static final Logger Log = LoggerFactory.getLogger(SpamReportManager.class);

    private final Set<SpamReportEventListener> listeners = new HashSet<>();

    private SpamReportProvider spamReportProvider = new DefaultSpamReportProvider();

    private static SpamReportManager instance = null;
    public synchronized static SpamReportManager getInstance() {
        if (instance == null) {
            instance = new SpamReportManager();
            instance.addListener(new SpamReportAdminNotifier());
        }
        return instance;
    }

    public void addListener(@Nonnull final SpamReportEventListener listener)
    {
        listeners.add(listener);
    }

    public void removeListener(@Nonnull final SpamReportEventListener listener)
    {
        listeners.remove(listener);
    }

    private void dispatchEvents(final SpamReport report)
    {
        Log.trace("Dispatching events for spam report: {}", report);
        listeners.forEach(listener -> {
            try {
                listener.receivedSpamReport(report);
            } catch (Throwable t) {
                Log.warn("An exception occurred while invoking listener with spam report {}", report, t);
            }
        });
    }

    public void process(final Set<SpamReport> spamReports)
    {
        Log.trace("Processing {} spam report(s)", spamReports.size());
        spamReports.forEach(spamReport -> {
            try {
                process(spamReport);
            } catch (Throwable t) {
                Log.warn("An exception occurred while processing spam report {}", spamReport, t);
            }
        });
    }

    public void process(final SpamReport spamReport)
    {
        Log.trace("Processing spam report: {}", spamReport);

        // Store the spam report.
        try {
            spamReportProvider.store(spamReport);
        } catch (Throwable t) {
            Log.warn("An exception occurred while storing spam report {}", spamReport, t);
        }

        // Invoke the event listeners
        dispatchEvents(spamReport);
    }
}
