/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.ant;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A simple ant task to return the sub directories of a given dir as a comma delimited string.
 *
 * This class does not need jdk 1.5 to compile.
 */
public class SubDirInfoTask extends Task {

    public static final String DEFAULT_DELIM = ",";

    private File dir;
    private String property;
    private String delimiter;
    private String ifexists;
    private String except;

    public SubDirInfoTask() {
    }

    public File getDir() {
        return dir;
    }

    public void setDir(File dir) {
        this.dir = dir;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public String getDelimiter() {
        if (delimiter == null) {
            return DEFAULT_DELIM;
        }
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getIfexists() {
        return ifexists;
    }

    public void setIfexists(String ifexists) {
        this.ifexists = ifexists;
    }

    public String getExcept() {
        return except;
    }

    public void setExcept(String except) {
        this.except = except;
    }

    public void execute() throws BuildException {
        // Get the siblings of the given directory, add sub directory names to the property
        List excepts;
        if (except != null) {
            excepts = Arrays.asList( except.split( getDelimiter() ) );
        } else {
            excepts = Collections.EMPTY_LIST;
        }
        File[] subdirs = dir.listFiles();
        StringBuffer buf = new StringBuffer();
        String value = null;
        String sep = "";
        if (subdirs != null) {
            for (int i=0; i<subdirs.length; i++) {
                File subdir = subdirs[i];
                boolean add = false;
                if (subdir.isDirectory()) {
                    if (getIfexists() != null) {
                        File file = new File(subdir, getIfexists());
                        if (file.exists()) {
                            add = true;
                        }
                    }
                    else {
                        add = true;
                    }
                }
                if (add && !excepts.contains(subdir.getName())) {
                    buf.append(sep).append(subdir.getName());
                    sep = getDelimiter();
                }
            }
        }
        if (buf.length() > 0) {
            value = buf.toString();
        }
        if (value == null) {
            log("No tokens found.", Project.MSG_DEBUG);
        }
        else {
            log("Setting property '" + property + "' to " + value, Project.MSG_DEBUG);
            if (buf.length() >= 0) {
                getProject().setProperty(property, value);
            }
        }
    }
}
