package org.jivesoftware.ant;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;

import java.io.File;

/**
 * A simple ant task to return the sub directories of a given dir as a comma delimited string.
 */
public class SubDirInfoTask extends Task {

    public static final String DEFAULT_DELIM = ",";

    private File dir;
    private String property;
    private String delimiter;
    private String ifexists;

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

    public void execute() throws BuildException {
        // Get the siblings of the given directory, add sub directory names to the property
        File[] subdirs = dir.listFiles();
        StringBuffer buf = new StringBuffer();
        String sep = "";
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
            if (add) {
                buf.append(sep).append(subdir.getName());
                sep = getDelimiter();
            }
        }
        if (buf.length() > 0) {
            getProject().setProperty(property, buf.toString());
        }
    }
}
