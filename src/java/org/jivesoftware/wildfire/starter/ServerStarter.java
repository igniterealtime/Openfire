/*
 * $RCSfile$
 * $Revision: 1089 $
 * $Date: 2005-03-07 02:36:27 -0300 (Mon, 07 Mar 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.starter;

import org.jivesoftware.util.Log;

import java.io.*;
import java.util.jar.Pack200;
import java.util.jar.JarOutputStream;

/**
 * Starts the core XMPP server. A bootstrap class that configures classloaders
 * to ensure easy, dynamic server startup.
 *
 * This class should be for standalone mode only. Wildfire servers launched
 * through a J2EE container (servlet/EJB) will use those environment's
 * classloading facilities to ensure proper startup.<p>
 *
 * Tasks:<ul>
 *      <li>Unpack any pack files in the lib directory (Pack200 encoded JAR files).</li>
 *      <li>Add all jars in the lib directory to the classpath.</li>
 *      <li>Add the config directory to the classpath for loadResource()</li>
 *      <li>Start the server</li>
 * </ul>
 *
 * Note: if the enviroment property <tt>wildfire.lib.directory</tt> is specified
 * ServerStarter will attempt to use this value as the value for wildfire's lib
 * directory. If the property is not specified the default value of ../lib will be used.
 *
 * @author Iain Shigeoka
 */
public class ServerStarter {

    /**
     * Default to this location if one has not been specified
     */
    private static final String DEFAULT_LIB_DIR = "../lib";

    public static void main(String [] args) {
        new ServerStarter().start();
    }

    /**
     * Starts the server by loading and instantiating the bootstrap
     * container. Once the start method is called, the server is
     * started and the server starter should not be used again.
     */
    private void start() {
        // setup the classpath using JiveClassLoader
        try {
            // Load up the bootstrap container
            final ClassLoader parent = findParentClassLoader();

            String libDirString = System.getProperty("wildfire.lib.dir");

            File libDir;
            if (libDirString != null) {
                // If the lib directory property has been specified and it actually
                // exists use it, else use the default
                libDir = new File(libDirString);
                if (!libDir.exists()) {
                    Log.warn("Lib directory " + libDirString +
                            " does not exist. Using default " + DEFAULT_LIB_DIR);
                    libDir = new File(DEFAULT_LIB_DIR);
                }
            }
            else {
                libDir = new File(DEFAULT_LIB_DIR);
            }

            // Unpack any pack files.
            unpackArchives(libDir);

            ClassLoader loader = new JiveClassLoader(parent, libDir);

            Thread.currentThread().setContextClassLoader(loader);
            Class containerClass = loader.loadClass(
                    "org.jivesoftware.wildfire.XMPPServer");
            containerClass.newInstance();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Locates the best class loader based on context (see class description).
     *
     * @return The best parent classloader to use
     */
    private ClassLoader findParentClassLoader() {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (parent == null) {
            parent = this.getClass().getClassLoader();
            if (parent == null) {
                parent = ClassLoader.getSystemClassLoader();
            }
        }
        return parent;
    }

    /**
     * Converts any pack files in a directory into standard JAR files. Each
     * pack file will be deleted after being converted to a JAR. If no
     * pack files are found, this method does nothing.
     *
     * @param libDir the directory containing pack files.
     */
    private void unpackArchives(File libDir) {
        // Get a list of all packed files in the lib directory.
        File [] packedFiles = libDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".pack");
            }
        });
        // Unpack each.
        boolean unpacked = false;
        for (File packedFile : packedFiles) {
            try {
                String jarName = packedFile.getName().substring(0,
                        packedFile.getName().length() - ".pack".length());
                InputStream in = new BufferedInputStream(new FileInputStream(packedFile));
                JarOutputStream out = new JarOutputStream(new BufferedOutputStream(
                        new FileOutputStream(new File(libDir, jarName))));
                Pack200.Unpacker unpacker = Pack200.newUnpacker();
                // Print something so the user knows something is happening.
                System.out.print(".");
                // Call the unpacker
                unpacker.unpack(in, out);

                in.close();
                out.close();
                packedFile.delete();
                unpacked = true;
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        // Print newline if unpacking happened.
        if (unpacked) {
            System.out.println();
        }
    }
}