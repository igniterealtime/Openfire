/**
 * $RCSfile$
 * $Revision: 1089 $
 * $Date: 2005-03-07 02:36:27 -0300 (Mon, 07 Mar 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.starter;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * A simple classloader to extend the classpath to
 * include all jars in a lib directory.<p>
 *
 * The new classpath includes all <tt>*.jar</tt> and <tt>*.zip</tt>
 * archives (zip is commonly used in packaging JDBC drivers). The extended
 * classpath is used for both the initial server startup, as well as loading
 * plug-in support jars.
 *
 * @author Derek DeMoro
 * @author Iain Shigeoka
 */
class JiveClassLoader extends URLClassLoader {

    /**
     * Constructs the classloader.
     *
     * @param parent the parent class loader (or null for none).
     * @param libDir the directory to load jar files from.
     * @throws java.net.MalformedURLException if the libDir path is not valid.
     */
    JiveClassLoader(ClassLoader parent, File libDir) throws MalformedURLException {
        super(new URL[] { libDir.toURL() }, parent);

        File[] jars = libDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                boolean accept = false;
                String smallName = name.toLowerCase();
                if (smallName.endsWith(".jar")) {
                    accept = true;
                }
                else if (smallName.endsWith(".zip")) {
                    accept = true;
                }
                return accept;
            }
        });

        // Do nothing if no jar or zip files were found
        if (jars == null) {
            return;
        }

        for (int i = 0; i < jars.length; i++) {
            if (jars[i].isFile()) {
                addURL(jars[i].toURL());
            }
        }
    }
}
