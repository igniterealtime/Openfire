/**
 * $RCSfile$
 * $Revision: 19201 $
 * $Date: 2005-06-30 15:04:29 -0700 (Thu, 30 Jun 2005) $
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MacroGroup {
    
    private List<Macro> macros;
    private List<MacroGroup> macroGroups;


    // Define MacroGroup
    private String title;

    public MacroGroup() {
        macros = new ArrayList<Macro>();
        macroGroups = new ArrayList<MacroGroup>();
    }

    public void addMacro(Macro macro) {
        macros.add(macro);
    }

    public void removeMacro(Macro macro) {
        macros.remove(macro);
    }

    public Macro getMacroByTitle(String title) {
        Collection<Macro> col = Collections.unmodifiableList(macros);
        for (Macro macro : col) {
            if (macro.getTitle().equalsIgnoreCase(title)) {
                return macro;
            }
        }
        return null;
    }

    public void addMacroGroup(MacroGroup group){
        macroGroups.add(group);
    }

    public void removeMacroGroup(MacroGroup group){
        macroGroups.remove(group);
    }

    public Macro getMacro(int location) {
        return macros.get(location);
    }

    public MacroGroup getMacroGroupByTitle(String title) {
        Collection<MacroGroup> col = Collections.unmodifiableList(macroGroups);
        for (MacroGroup group : col) {
            if (group.getTitle().equalsIgnoreCase(title)) {
                return group;
            }
        }
        return null;
    }

    public MacroGroup getMacroGroup(int location){
        return macroGroups.get(location);
    }


    public List<Macro> getMacros() {
        return macros;
    }

    public void setMacros(List<Macro> macros) {
        this.macros = macros;
    }

    public List<MacroGroup> getMacroGroups() {
        return macroGroups;
    }

    public void setMacroGroups(List<MacroGroup> macroGroups) {
        this.macroGroups = macroGroups;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
