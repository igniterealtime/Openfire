/**
 * $RCSfile$
 * $Revision: 27699 $
 * $Date: 2006-02-23 11:32:48 -0800 (Thu, 23 Feb 2006) $
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
package org.jivesoftware.xmpp.workgroup;

import org.jivesoftware.openfire.fastpath.providers.*;
import org.jivesoftware.openfire.fastpath.dataforms.WorkgroupFormProvider;
import org.jivesoftware.openfire.fastpath.macros.MacroProvider;
import org.jivesoftware.openfire.fastpath.settings.offline.OfflineSettingsProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Registration and delegation class for WorkgroupProviders. Use this class to register
 * your own Workgroup Providers.
 *
 * @see WorkgroupProvider
 */
public class WorkgroupProviderManager {

    private static WorkgroupProviderManager singleton = new WorkgroupProviderManager();

    private final List<WorkgroupProvider> providers = new ArrayList<WorkgroupProvider>();


    /**
     * Returns the singleton instance of <CODE>WorkgroupProviderManager</CODE>,
     * creating it if necessary.
     * <p/>
     *
     * @return the singleton instance of <Code>WorkgroupProviderManager</CODE>
     */
    public static WorkgroupProviderManager getInstance() {
        return singleton;
    }

    public static void shutdown() {
        singleton = null;
    }

    private WorkgroupProviderManager() {
        // Private constructor for singleton design.
        providers.add(new AgentHistory());
        providers.add(new ChatNotes());
        providers.add(new OfflineSettingsProvider());
        providers.add(new SearchProvider());
        providers.add(new WorkgroupFormProvider());
        providers.add(new MacroProvider());
        providers.add(new ChatMetadataProvider());
        providers.add(new SiteTracker());
        providers.add(new SoundProvider());
        providers.add(new WorkgroupPropertiesProvider());
        providers.add(new MetadataProvider());
        providers.add(new MonitorProvider());

        final EmailProvider emailProvider = new EmailProvider();
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        workgroupManager.getIqDiscoInfoHandler().addServerFeaturesProvider(emailProvider);

        providers.add(emailProvider);
    }

    public void addWorkgroupProvider(WorkgroupProvider provider) {
        providers.add(provider);
    }

    public void removeWorkgroupProvider(WorkgroupProvider provider) {
        providers.remove(provider);
    }

    public Collection<WorkgroupProvider> getWorkgroupProviders() {
        return Collections.unmodifiableCollection(providers);
    }
}
