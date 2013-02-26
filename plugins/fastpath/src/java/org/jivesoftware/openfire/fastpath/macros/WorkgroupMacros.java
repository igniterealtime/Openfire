/**
 * $RCSfile$
 * $Revision: 25826 $
 * $Date: 2006-01-17 10:00:38 -0800 (Tue, 17 Jan 2006) $
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

package org.jivesoftware.openfire.fastpath.macros;

import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.xmpp.workgroup.DbProperties;
import org.jivesoftware.xmpp.workgroup.UnauthorizedException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.XStream;

public class WorkgroupMacros {

	private static final Logger Log = LoggerFactory.getLogger(WorkgroupMacros.class);
	
    private Map<Workgroup, MacroGroup> rootGroups = new HashMap<Workgroup, MacroGroup>();

    private static WorkgroupMacros singleton;

    private static final Object LOCK = new Object();
    private XStream xstream = new XStream();

    /**
     * Returns the singleton instance of <CODE>WorkgroupMacros</CODE>,
     * creating it if necessary.
     * <p/>
     *
     * @return the singleton instance of <Code>WorkgroupMacros</CODE>
     */
    public static WorkgroupMacros getInstance() {
        // Synchronize on LOCK to ensure that we don't end up creating
        // two singletons.
        synchronized (LOCK) {
            if (singleton == null) {
                WorkgroupMacros WorkgroupMacros = new WorkgroupMacros();
                singleton = WorkgroupMacros;
                return WorkgroupMacros;
            }
        }
        return singleton;
    }

    private WorkgroupMacros() {
        // Load macros
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        xstream.alias("macro", Macro.class);
        xstream.alias("macrogroup", MacroGroup.class);


        for (Workgroup workgroup : workgroupManager.getWorkgroups()) {
            // Load from DB.
            DbProperties prop = workgroup.getProperties();
            String macros = prop.getProperty("jive.macro" + workgroup.getID());
            if (ModelUtil.hasLength(macros)) {
                MacroGroup group = (MacroGroup)xstream.fromXML(macros);
                rootGroups.put(workgroup, group);
            }
        }

    }

    public MacroGroup getMacroGroup(Workgroup workgroup) {
        if (rootGroups.containsKey(workgroup)) {
            return rootGroups.get(workgroup);
        }

        MacroGroup rootGroup = new MacroGroup();
        rootGroup.setTitle("Parent Category");
        rootGroups.put(workgroup, rootGroup);
        return rootGroup;
    }

    public MacroGroup getMacroGroup(Workgroup workgroup, String name) {
        final MacroGroup rootGroup = getMacroGroup(workgroup);
        if (rootGroup.getTitle().equals(name)) {
            return rootGroup;
        }

        for (MacroGroup groups : rootGroup.getMacroGroups()) {
            if (groups.getTitle().equals(name)) {
                return groups;
            }

            MacroGroup foundGroup = getChildGroup(groups, name);
            if (foundGroup != null) {
                return foundGroup;
            }
        }
        return null;
    }

    private MacroGroup getChildGroup(MacroGroup rootGroup, String name) {
        MacroGroup returnGroup = null;
        for (MacroGroup group : rootGroup.getMacroGroups()) {
            if (group.getTitle().equals(name)) {
                returnGroup = group;
                break;
            }
            else {
                returnGroup = getChildGroup(group, name);
                if (returnGroup != null) {
                    break;
                }
            }
        }


        return returnGroup;
    }

    public void setRootGroup(Workgroup workgroup, MacroGroup rootGroup) {
        rootGroups.put(workgroup, rootGroup);
    }

    public void saveMacros(Workgroup workgroup) {
        long id = workgroup.getID();
        MacroGroup group = getMacroGroup(workgroup);

        String saveString = xstream.toXML(group);

        DbProperties props = workgroup.getProperties();
        try {
            props.deleteProperty("jive.macro" + id);
            props.setProperty("jive.macro" + id, saveString);
        }
        catch (UnauthorizedException e) {
           Log.error(e.getMessage(), e);
        }
    }

}
