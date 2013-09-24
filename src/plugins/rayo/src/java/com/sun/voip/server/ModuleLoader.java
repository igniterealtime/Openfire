/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */

package com.sun.voip.server;

import com.sun.voip.Logger;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ModuleLoader extends URLClassLoader {

    private List<Class<?>> moduleClasses = new ArrayList<Class<?>>();

    /*
     * modPath is a list of paths to jar files.
     * Each path element in the list is separated by path.separator
     */
    public ModuleLoader(String modPath) throws IOException {
	super(new URL[0]);

        
	loadModules(modPath);
	initializeModules();
    }

    public void loadModules(String modPath) throws IOException {
	String[] path = modPath.split(
	    System.getProperty("path.separator"));

	for (int i = 0; i < path.length; i++) {
	    Logger.writeFile("Searching for modules in " + path[i]);

	    File f = new File(path[i]);

	    String[] fileList = f.list();

	    if (fileList == null) {
	        Logger.writeFile("module path '" + path[i] + "' "
		    + "is not a directory.  Ignoring.");   
		continue;
	    }

	    for (int j = 0; j < fileList.length; j++) {
                String dirEntry = fileList[j];   // next directory entry

		if (dirEntry.equals(".") || dirEntry.equals("..")) {
		    continue;	// skip these
		}

		String dir = path[i] + dirEntry 
		    + System.getProperty("file.separator");

		if (new File(dir).isDirectory()) {
		    /*
		     * Recurively load jar files in subdirectories
		     */
		    loadModules(dir);
		    continue;
		}

	        if (dirEntry.indexOf(".jar") < 0) {
		    if (Logger.logLevel >= Logger.LOG_INFO) {
		        Logger.println("Skipping non-jar file " + dirEntry);
		    }
		    continue;
	        }

	        dirEntry = path[i] + dirEntry;

	        Logger.println("Processing jar file " + dirEntry);

		try {
	            addURL(new File(dirEntry).toURI().toURL());
	            
                    loadBridgeModule(dirEntry);
                    //if (loadBridgeModule(dirEntry) == false) {
		    //	loadNonBridgeModule(dirEntry);
		    //}
		} catch (IOException e) {
		    Logger.println("Can't read jar file:  " + dirEntry);
		    continue;
		}
	    }
	}
    }

    private void initializeModules() throws IOException {
        for (Class<?> c : moduleClasses) {
            
            if (Logger.logLevel >= Logger.LOG_INFO) {
                Logger.println("initializeModule " + c.getName());
	    }

            try {
                c.newInstance();
            } catch (Exception ex) {
                Logger.exception("Error instantiating " + c, ex);
                continue;
            }
        }
    }

    /*
     * The manifest must have an attribute with the name Bridge-Module-Info.
     * The value has the following format:
     *
     *  <classname>,<classname> ...
     *
     * where classname is the class to instiate.
     */
    private boolean loadBridgeModule(String dirEntry) throws IOException {
	JarFile jarFile = new JarFile(dirEntry);

	Manifest manifest = null;

	try {
	    manifest = jarFile.getManifest();
	} catch (IOException e) {
	    Logger.println("can't read manifest in " + jarFile);
	    return false;
	}

        if (manifest == null) {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("no manifest in " + jarFile);
	    }
	    return false;
	}

	String[] classList = null;

	Attributes attributes = manifest.getMainAttributes();

	if (attributes == null) {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("No attributes in " + dirEntry);
	    }
	    return false;
	}

	String attributeValues = attributes.getValue("Bridge-Module-Info");

	if (attributeValues == null) {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
		Logger.println("No attribute values in:  " + dirEntry);
	    }

	    return false;
	}

	String[] moduleValues = attributeValues.split(",");

	for (int i = 0; i < moduleValues.length; i++) {
	    addModule(moduleValues[i].trim());
	}

	return true;
    }

    private void addModule(String className) {
	Class c;

        try {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("Looking for class " + className);
	    }

            c = loadClass(className);
        } catch (ClassNotFoundException e) {
            Logger.println("ClassNotFoundException:  '" + className + "'");
	    return;
	}
		
	moduleClasses.add(c);
	return;
    }

    private void loadNonBridgeModule(String dirEntry) throws IOException {
	JarFile jarFile = new JarFile(dirEntry);

	Enumeration entries = jarFile.entries();

	if (entries == null) {
	    Logger.println("No entries in jarFile:  " + dirEntry);
	    return;

	}

	while (entries.hasMoreElements()) {
	    JarEntry jarEntry = (JarEntry) entries.nextElement();

	    String className = jarEntry.getName();

	    int ix;

	    if ((ix = className.indexOf(".class")) < 0) {
		if (Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("Skipping non-class entry in jarFile:  " 
			+ className);
		}
		continue;
	    }

	    className = className.replaceAll(".class", "");
	    className = className.replaceAll("/", ".");

            try {
		if (Logger.logLevel >= Logger.LOG_INFO) {
		    Logger.println("Looking for class '" + className + "'");
		}

                loadClass(className);   	// load the class
            } catch (ClassNotFoundException e) {
                Logger.println("ClassNotFoundException:  '" + className + "'");
	    }
        }
    }
}
