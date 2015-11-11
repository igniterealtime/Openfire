/**
 * $RCSfile$
 * $Revision: 2993 $
 * $Date: 2005-10-24 18:11:33 -0300 (Mon, 24 Oct 2005) $
 *
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

package org.jivesoftware.openfire.container;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.AccessControlException;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.jivesoftware.openfire.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClassLoader for plugins. It searches the plugin directory for classes and JAR
 * files, then constructs a class loader for the resources found. Resources are
 * loaded as follows:
 * <ul>
 * <li>Any JAR files in the <tt>lib</tt> will be added to the classpath.</li>
 * <li>Any files in the classes directory will be added to the classpath.</li>
 * </ul>
 *
 * @author Derek DeMoro
 * @author wmz7year
 */
public class PluginClassLoader extends URLClassLoader {
	private static final Logger Log = LoggerFactory.getLogger(PluginClassLoader.class);

	private static final String CLASS_FILE_SUFFIX = ".class";
	private static final Manifest MANIFEST_UNKNOWN = new Manifest();

	/**
	 * The cache of ResourceEntry for classes and resources we have loaded,
	 * keyed by resource path, not binary name. Path is used as the key since
	 * resources may be requested by binary name (classes) or path (other
	 * resources such as property files) and the mapping from binary name to
	 * path is unambiguous but the reverse mapping is ambiguous.
	 */
	protected final Map<String, ResourceEntry> resourceEntries = new ConcurrentHashMap<>();

	/**
	 * Repositories managed by this class rather than the super class.
	 */
	private List<URL> localRepositories = new ArrayList<>();

	private ClassLoader parent;

	public PluginClassLoader() {
		super(new URL[] {}, findParentClassLoader());
		this.parent = findParentClassLoader();
	}

	/**
	 * Find the resource with the given name, and return an input stream that
	 * can be used for reading it. The search order is as described for
	 * <code>getResource()</code>, after checking to see if the resource data
	 * has been previously cached. If the resource cannot be found, return
	 * <code>null</code>.
	 *
	 * @param name
	 *            Name of the resource to return an input stream for
	 */
	@Override
	public InputStream getResourceAsStream(String name) {
		if (Log.isDebugEnabled())
			Log.debug("getResourceAsStream(" + name + ")");

		InputStream stream = null;

		// Check for a cached copy of this resource
		stream = findLoadedResource(name);
		if (stream != null) {
			if (Log.isDebugEnabled())
				Log.debug("  --> Returning stream from cache");
			return (stream);
		}

		// Search local repositories
		if (Log.isDebugEnabled())
			Log.debug("  Searching local repositories");
		URL url = findResource(name);
		if (url != null) {
			if (Log.isDebugEnabled())
				Log.debug("  --> Returning stream from local");
			stream = findLoadedResource(name);
			try {
				if (stream == null)
					stream = url.openStream();
			} catch (IOException e) {
				// Ignore
			}
			if (stream != null)
				return (stream);
		}

		// Delegate to parent unconditionally
		if (Log.isDebugEnabled())
			Log.debug("  Delegating to parent classloader unconditionally " + parent);
		stream = parent.getResourceAsStream(name);
		if (stream != null) {
			if (Log.isDebugEnabled())
				Log.debug("  --> Returning stream from parent");
			return (stream);
		}

		// Resource was not found
		if (Log.isDebugEnabled())
			Log.debug("  --> Resource not found, returning null");
		return (null);

	}

	/**
	 * Finds the resource with the given name if it has previously been loaded
	 * and cached by this class loader, and return an input stream to the
	 * resource data. If this resource has not been cached, return
	 * <code>null</code>.
	 *
	 * @param name
	 *            Name of the resource to return
	 */
	protected InputStream findLoadedResource(String name) {

		String path = nameToPath(name);

		ResourceEntry entry = resourceEntries.get(path);
		if (entry != null) {
			if (entry.binaryContent != null)
				return new ByteArrayInputStream(entry.binaryContent);
			else {
				try {
					return entry.source.openStream();
				} catch (IOException ioe) {
					// Ignore
				}
			}
		}
		return null;
	}

	/**
	 * Find the specified resource in our local repository, and return a
	 * <code>URL</code> referring to it, or <code>null</code> if this resource
	 * cannot be found.
	 *
	 * @param name
	 *            Name of the resource to be found
	 */
	@Override
	public URL findResource(String name) {

		if (Log.isDebugEnabled())
			Log.debug("    findResource(" + name + ")");

		URL url = null;

		String path = nameToPath(name);

		ResourceEntry entry = findResourceInternal(name, path, false);

		if (entry != null) {
			url = entry.source;
		}

		if (url == null) {
			url = super.findResource(name);
		}

		if (Log.isDebugEnabled()) {
			if (url != null)
				Log.debug("    --> Returning '" + url.toString() + "'");
			else
				Log.debug("    --> Resource not found, returning null");
		}
		return (url);

	}

	/**
	 * Load the class with the specified name. This method searches for classes
	 * in the same manner as <code>loadClass(String, boolean)</code> with
	 * <code>false</code> as the second argument.
	 *
	 * @param name
	 *            The binary name of the class to be loaded
	 *
	 * @exception ClassNotFoundException
	 *                if the class was not found
	 */
	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return (loadClass(name, false));
	}

	/**
	 * Load the class with the specified name, searching using the following
	 * algorithm until it finds and returns the class. If the class cannot be
	 * found, returns <code>ClassNotFoundException</code>.
	 * <ul>
	 * <li>Call <code>findLoadedClass(String)</code> to check if the class has
	 * already been loaded. If it has, the same <code>Class</code> object is
	 * returned.</li>
	 * <li>If the <code>delegate</code> property is set to <code>true</code>,
	 * call the <code>loadClass()</code> method of the parent class loader, if
	 * any.</li>
	 * <li>Call <code>findClass()</code> to find this class in our locally
	 * defined repositories.</li>
	 * <li>Call the <code>loadClass()</code> method of our parent class loader,
	 * if any.</li>
	 * </ul>
	 * If the class was found using the above steps, and the
	 * <code>resolve</code> flag is <code>true</code>, this method will then
	 * call <code>resolveClass(Class)</code> on the resulting Class object.
	 *
	 * @param name
	 *            The binary name of the class to be loaded
	 * @param resolve
	 *            If <code>true</code> then resolve the class
	 *
	 * @exception ClassNotFoundException
	 *                if the class was not found
	 */
	@Override
	public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			if (Log.isDebugEnabled())
				Log.debug("loadClass(" + name + ", " + resolve + ")");

			Class<?> clazz = null;

			// Check our previously loaded local class cache
			clazz = findLoadedClass0(name);
			if (clazz != null) {
				if (Log.isDebugEnabled())
					Log.debug("  Returning class from cache");
				if (resolve)
					resolveClass(clazz);
				return (clazz);
			}

			// Check our previously loaded class cache
			clazz = findLoadedClass(name);
			if (clazz != null) {
				if (Log.isDebugEnabled())
					Log.debug("  Returning class from cache");
				if (resolve)
					resolveClass(clazz);
				return (clazz);
			}

			// Search local repositories
			if (Log.isDebugEnabled())
				Log.debug("  Searching local repositories");
			try {
				clazz = findClass(name);
				if (clazz != null) {
					if (Log.isDebugEnabled())
						Log.debug("  Loading class from local repository");
					if (resolve)
						resolveClass(clazz);
					return (clazz);
				}
			} catch (ClassNotFoundException e) {
				// Ignore
			}

			// Delegate to parent unconditionally
			if (Log.isDebugEnabled())
				Log.debug("  Delegating to parent classloader at end: " + parent);
			try {
				clazz = Class.forName(name, false, parent);
				if (clazz != null) {
					if (Log.isDebugEnabled())
						Log.debug("  Loading class from parent");
					if (resolve)
						resolveClass(clazz);
					return (clazz);
				}
			} catch (ClassNotFoundException e) {
				// Ignore
			}
		}

		throw new ClassNotFoundException(name);
	}

	/**
	 * Find the specified class in our local repositories, if possible. If not
	 * found, throw <code>ClassNotFoundException</code>.
	 *
	 * @param name
	 *            The binary name of the class to be loaded
	 *
	 * @exception ClassNotFoundException
	 *                if the class was not found
	 */
	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		if (Log.isDebugEnabled())
			Log.debug("    findClass(" + name + ")");

		// Ask our superclass to locate this class, if possible
		// (throws ClassNotFoundException if it is not found)
		Class<?> clazz = null;
		try {
			if (Log.isTraceEnabled())
				Log.trace("      findClassInternal(" + name + ")");
			try {
				clazz = findClassInternal(name);
			} catch (AccessControlException ace) {
				Log.warn("PluginClassLoader.findClassInternal(" + name + ") security exception: " + ace.getMessage(),
						ace);
				throw new ClassNotFoundException(name, ace);
			} catch (RuntimeException e) {
				if (Log.isTraceEnabled())
					Log.trace("      -->RuntimeException Rethrown", e);
				throw e;
			}
			if (clazz == null) {
				try {
					clazz = super.findClass(name);
				} catch (AccessControlException ace) {
					Log.warn(
							"PluginClassLoader.findClassInternal(" + name + ") security exception: " + ace.getMessage(),
							ace);
					throw new ClassNotFoundException(name, ace);
				} catch (RuntimeException e) {
					if (Log.isTraceEnabled())
						Log.trace("      -->RuntimeException Rethrown", e);
					throw e;
				}
			}
			if (clazz == null) {
				if (Log.isDebugEnabled())
					Log.debug("    --> Returning ClassNotFoundException");
				throw new ClassNotFoundException(name);
			}
		} catch (ClassNotFoundException e) {
			if (Log.isTraceEnabled())
				Log.trace("    --> Passing on ClassNotFoundException");
			throw e;
		}

		// Return the class we have located
		if (Log.isTraceEnabled())
			Log.debug("      Returning class " + clazz);
		return (clazz);
	}

	/**
	 * Find specified class in local repositories.
	 *
	 * @param name
	 *            The binary name of the class to be loaded
	 *
	 * @return the loaded class, or null if the class isn't found
	 */
	protected Class<?> findClassInternal(String name) {
		String path = binaryNameToPath(name, true);

		ResourceEntry entry = findResourceInternal(name, path, true);

		if (entry == null) {
			return null;
		}

		Class<?> clazz = entry.loadedClass;
		if (clazz != null)
			return clazz;

		synchronized (getClassLoadingLock(name)) {
			clazz = entry.loadedClass;
			if (clazz != null)
				return clazz;

			if (entry.binaryContent == null) {
				return null;
			}

			try {
				clazz = defineClass(name, entry.binaryContent, 0, entry.binaryContent.length,
						new CodeSource(entry.codeBase, entry.certificates));
			} catch (UnsupportedClassVersionError ucve) {
				throw new UnsupportedClassVersionError(ucve.getLocalizedMessage() + " " + name);
			}
			// Now the class has been defined, clear the elements of the local
			// resource cache that are no longer required.
			entry.loadedClass = clazz;
			entry.binaryContent = null;
			entry.codeBase = null;
			entry.manifest = null;
			entry.certificates = null;
			// Retain entry.source in case of a getResourceAsStream() call on
			// the class file after the class has been defined.
		}
		return clazz;
	}

	/**
	 * Find specified resource in local repositories.
	 *
	 * @return the loaded resource, or null if the resource isn't found
	 */
	protected ResourceEntry findResourceInternal(final String name, final String path, boolean manifestRequired) {
		ResourceEntry entry = resourceEntries.get(path);
		return entry;
	}

	/**
	 * Find the resource with the given name. A resource is some data (images,
	 * audio, text, etc.) that can be accessed by class code in a way that is
	 * independent of the location of the code. The name of a resource is a
	 * "/"-separated path name that identifies the resource. If the resource
	 * cannot be found, return <code>null</code>.
	 * <p>
	 * This method searches according to the following algorithm, returning as
	 * soon as it finds the appropriate URL. If the resource cannot be found,
	 * returns <code>null</code>.
	 * <ul>
	 * <li>If the <code>delegate</code> property is set to <code>true</code>,
	 * call the <code>getResource()</code> method of the parent class loader, if
	 * any.</li>
	 * <li>Call <code>findResource()</code> to find this resource in our locally
	 * defined repositories.</li>
	 * <li>Call the <code>getResource()</code> method of the parent class
	 * loader, if any.</li>
	 * </ul>
	 *
	 * @param name
	 *            Name of the resource to return a URL for
	 */
	@Override
	public URL getResource(String name) {
		if (Log.isDebugEnabled())
			Log.debug("getResource(" + name + ")");

		URL url = null;

		// Search local repositories
		url = findResource(name);
		if (url != null) {
			if (Log.isDebugEnabled())
				Log.debug("  --> Returning '" + url.toString() + "'");
			return (url);
		}

		// Delegate to parent unconditionally if not already attempted
		url = parent.getResource(name);
		if (url != null) {
			if (Log.isDebugEnabled())
				Log.debug("  --> Returning '" + url.toString() + "'");
			return (url);
		}

		// Resource was not found
		if (Log.isDebugEnabled())
			Log.debug("  --> Resource not found, returning null");
		return (null);

	}

	/**
	 * Return an enumeration of <code>URLs</code> representing all of the
	 * resources with the given name. If no resources with this name are found,
	 * return an empty enumeration.
	 *
	 * @param name
	 *            Name of the resources to be found
	 *
	 * @exception IOException
	 *                if an input/output error occurs
	 */
	public Enumeration<URL> getResources(String name) throws IOException {
		if (Log.isDebugEnabled())
			Log.debug("    findResources(" + name + ")");

		LinkedHashSet<URL> result = new LinkedHashSet<>();

		String path = nameToPath(name);

		// find local Classloader
		Iterator<Entry<String, ResourceEntry>> iterator = resourceEntries.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<String, ResourceEntry> resource = iterator.next();
			String loadedResourceName = resource.getKey();
			if (loadedResourceName.endsWith(path)) {
				// path to name
				loadedResourceName = pathToName(loadedResourceName);
				result.add(findResource(loadedResourceName));
			}
		}

		// find from parent Classloader
		Enumeration<URL> parentResources = super.getResources(name);
		while (parentResources.hasMoreElements()) {
			result.add(parentResources.nextElement());
		}

		return Collections.enumeration(result);
	}

	/**
	 * Adds a directory to the class loader.
	 *
	 * @param directory
	 *            the directory.
	 * @param developmentMode
	 *            true if the plugin is running in development mode. This
	 *            resolves classloader conflicts between the deployed plugin and
	 *            development classes.
	 */
	public void addDirectory(File directory, boolean developmentMode) {
		try {
			// Add classes directory to classpath.
			File classesDir = new File(directory, "classes");
			if (classesDir.exists()) {
				addResource(classesDir.toURI().toURL(), "/");
			}

			// Add i18n directory to classpath.
			File databaseDir = new File(directory, "database");
			if (databaseDir.exists()) {
				addResource(databaseDir.toURI().toURL(), "/");
			}

			// Add i18n directory to classpath.
			File i18nDir = new File(directory, "i18n");
			if (i18nDir.exists()) {
				addResource(i18nDir.toURI().toURL(), "/");
			}

			// Add web directory to classpath.
			File webDir = new File(directory, "web");
			if (webDir.exists()) {
				addResource(webDir.toURI().toURL(), "/");
			}

			// Add lib directory to classpath.
			File libDir = new File(directory, "lib");
			File[] jars = libDir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".jar") || name.endsWith(".zip");
				}
			});
			if (jars != null) {
				for (int i = 0; i < jars.length; i++) {
					if (jars[i] != null && jars[i].isFile()) {
						String jarFileUri = jars[i].toURI().toString() + "!/";
						if (developmentMode) {
							// Do not add plugin-pluginName.jar to classpath.
							if (!jars[i].getName().equals("plugin-" + directory.getName() + ".jar")) {
								addResource(new URL("jar", "", -1, jarFileUri), "/");
							}
						} else {
							addResource(new URL("jar", "", -1, jarFileUri), "/");
						}
					}
				}
			}
		} catch (MalformedURLException mue) {
			Log.error(mue.getMessage(), mue);
		}
	}

	/**
	 * Finds the class with the given name if it has previously been loaded and
	 * cached by this class loader, and return the Class object. If this class
	 * has not been cached, return <code>null</code>.
	 *
	 * @param name
	 *            The binary name of the resource to return
	 */
	protected Class<?> findLoadedClass0(String name) {

		String path = binaryNameToPath(name, true);

		ResourceEntry entry = resourceEntries.get(path);
		if (entry != null) {
			return entry.loadedClass;
		}
		return null;
	}

	private String binaryNameToPath(String binaryName, boolean withLeadingSlash) {
		// 1 for leading '/', 6 for ".class"
		StringBuilder path = new StringBuilder(7 + binaryName.length());
		if (withLeadingSlash) {
			path.append('/');
		}
		path.append(binaryName.replace('.', '/'));
		path.append(CLASS_FILE_SUFFIX);
		return path.toString();
	}

	/**
	 * Add the given URL to the classpath for this class loader, caching the JAR
	 * file connection so it can be unloaded later
	 * 
	 * @param file
	 *            URL for the JAR file or directory to append to classpath
	 */
	public void addURLFile(URL file) {
		addResource(file, "/");
	}

	/**
	 * Unload any JAR files that have been cached by this plugin
	 */
	public void unloadJarFiles() {
		resourceEntries.clear();
	}

	private void addResource(URL url, String parent) {
		// check file is directory and list files
		try {
			File file = new File(url.toURI());
			if (file.isDirectory()) {
				File[] files = file.listFiles();
				for (File temp : files) {
					if (temp.isDirectory()) {
						parent = String.format("%s%s/", parent, temp.getName());
					}
					addResource(temp.toURI().toURL(), parent);
				}
				return;
			}
		} catch (Exception e) {
			// do nothing
		}
		try {
			// check the resource is jar
			URLConnection uc = url.openConnection();
			if (uc instanceof JarURLConnection) {
				uc.setUseCaches(true);
				JarFile jarFile = ((JarURLConnection) uc).getJarFile();

				localRepositories.add(url);

				addJarResource(jarFile, url);
			} else {
				File file = new File(url.toURI());
				ResourceEntry entry = new ResourceEntry();
				entry.manifest = MANIFEST_UNKNOWN;
				entry.source = url;
				entry.codeBase = url;
				entry.lastModified = file.lastModified();
				entry.binaryContent = readBytes(new FileInputStream(file));
				if (Log.isDebugEnabled())
					Log.debug("add resource path:" + parent + file.getName());
				resourceEntries.put(parent + file.getName(), entry);
			}
		} catch (Exception e) {
			Log.warn("Failed to load plugin resource: " + url.toExternalForm());

		}

	}

	/**
	 * add jar file entries to the loacl repositories
	 */
	private void addJarResource(JarFile jarFile, URL codebase) {
		try {
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				JarEntry jarEntry = entries.nextElement();
				ResourceEntry entry = new ResourceEntry();
				entry.certificates = jarEntry.getCertificates();
				entry.manifest = jarFile.getManifest();
				entry.lastModified = jarEntry.getTime();
				entry.binaryContent = readBytes(jarFile.getInputStream(jarEntry));
				entry.codeBase = codebase;
				entry.source = new URL(codebase + jarEntry.getName());
				if (Log.isDebugEnabled())
					Log.debug("add resource path:/" + jarEntry.getName());
				resourceEntries.put("/" + jarEntry.getName(), entry);
			}
		} catch (IOException e) {
			Log.warn("Failed to load plugin lib resource: " + jarFile);
		}
	}

	/**
	 * read bytes from inputstream
	 */
	private byte[] readBytes(InputStream is) throws IOException {
		if (is == null) {
			return null;
		}
		try {
			ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
			byte[] buff = new byte[100];
			int rc = 0;
			while ((rc = is.read(buff, 0, 100)) > 0) {
				swapStream.write(buff, 0, rc);
			}
			byte[] result = swapStream.toByteArray();
			return result;
		} catch (IOException e) {
			throw e;
		} finally {
			is.close();
		}
	}

	private String nameToPath(String name) {
		if (name.startsWith("/")) {
			return name;
		}
		StringBuilder path = new StringBuilder(1 + name.length());
		path.append('/');
		path.append(name);
		return path.toString();
	}

	private String pathToName(String path) {
		if (path.length() <= 1) {
			throw new IllegalStateException(path);
		}
		if (!path.startsWith("/")) {
			return path;
		}
		return path.substring(1, path.length());
	}

	/**
	 * Locates the best parent class loader based on context.
	 *
	 * @return the best parent classloader to use.
	 */
	private static ClassLoader findParentClassLoader() {
		ClassLoader parent = XMPPServer.class.getClassLoader();
		if (parent == null) {
			parent = PluginClassLoader.class.getClassLoader();
		}
		if (parent == null) {
			parent = ClassLoader.getSystemClassLoader();
		}
		return parent;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Note that list of URLs returned by this method may not be complete. The
	 * web application class loader accesses class loader resources via the
	 * {@link WebResourceRoot} which supports the arbitrary mapping of
	 * additional files, directories and contents of JAR files under
	 * WEB-INF/classes. Any such resources will not be included in the URLs
	 * returned here.
	 */
	@Override
	public URL[] getURLs() {
		ArrayList<URL> result = new ArrayList<>();
		result.addAll(localRepositories);
		result.addAll(Arrays.asList(super.getURLs()));
		return result.toArray(new URL[result.size()]);
	}
}
