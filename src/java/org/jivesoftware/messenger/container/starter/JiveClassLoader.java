/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container.starter;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;


/**
 * <p>A simple classloader to extend the classpath to
 * include all jars in a lib directory.</p>
 * <p/>
 * <p>The new classpath includes all <tt>*.jar</tt> and <tt>*.zip</tt>
 * archives (zip is commonly used in packaging JDBC drivers). The extended
 * classpath is used for both the initial server startup, as well as loading
 * plug-in support jars.</p>
 *
 * @author Derek DeMoro
 * @author Iain Shigeoka
 */
class JiveClassLoader extends URLClassLoader {
    /**
     * <p>Create the classloader.</p>
     *
     * @param parent The parent class loader (or null for none)
     * @param libDir The directory to load jar files from
     * @throws java.net.MalformedURLException If the libDir path is not valid
     */
    JiveClassLoader(ClassLoader parent, File libDir) throws MalformedURLException {
        super(new URL[]{libDir.toURL()}, parent);

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
