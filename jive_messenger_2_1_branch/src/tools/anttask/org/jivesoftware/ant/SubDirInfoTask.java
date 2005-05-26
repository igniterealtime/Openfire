/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.ant;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import java.io.File;

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
                if (add && !subdir.getName().equals(except)) {
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
