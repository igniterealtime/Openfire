//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.util;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.jar.JarFile;

public class WebAppLoaderFix
{
    public static void checkAndClose(ClassLoader classLoader)
    {
        if (!isWindows() || !(classLoader instanceof URLClassLoader))
        {
           return;
        }
        HashSet<String> leakedJarNames = preClose((URLClassLoader)classLoader);
        cleanupJarFileFactory(leakedJarNames);
    }

    private static boolean isWindows()
    {
       String osProp = System.getProperty("os.name").toLowerCase();
       return osProp.indexOf("win") >= 0;
    }

    private static HashSet<String> preClose(URLClassLoader loader)
    {
        HashSet<String> leakedJarNames = new HashSet<>();
        Field f = getClassField(URLClassLoader.class, "ucp");
        if (f != null)
        {
            Object obj = null;
            try
            {
                obj = f.get(loader);
                final Object ucp = obj;
                f = getClassField(ucp.getClass(), "loaders");
                if (f != null)
                {
                    ArrayList loaders = null;
                    try
                    {
                        loaders = (ArrayList) f.get(ucp);
                    }
                    catch (IllegalAccessException ex)
                    {
                    }
                    for (int i = 0; loaders != null && i < loaders.size(); i++)
                    {
                        obj = loaders.get(i);
                        f = getClassField(obj.getClass(), "jar");
                        if (f != null)
                        {
                            try
                            {
                                obj = f.get(obj);
                            } catch (IllegalAccessException ex)
                            {
                            }
                            if (obj instanceof JarFile)
                            {
                                final JarFile jarFile = (JarFile) obj;
                                leakedJarNames.add(jarFile.getName());
                            }
                        }
                    }
                }
            }
            catch (IllegalAccessException ex)
            {
            }
        }
        return leakedJarNames;
    }

    private static Field getClassField(Class clz, String fieldName)
    {
        Field field = null;
        try
        {
            field = clz.getDeclaredField(fieldName);
            field.setAccessible(true);
        }
        catch (Exception e)
        {
        }
        return field;
    }

    private static void cleanupJarFileFactory(HashSet<String> leakedJarNames) {

        Class classJarURLConnection = null;
        try
        {
            classJarURLConnection = Class.forName("sun.net.www.protocol.jar.JarURLConnection");
        }
        catch (ClassNotFoundException ex)
        {
            return;
        }

        Field f = getClassField(classJarURLConnection, "factory");

        if (f == null)
        {
            return;
        }
        Object obj = null;
        try
        {
            obj = f.get(null);
        } catch (IllegalAccessException ex)
        {
            return;
        }

        Class classJarFileFactory = obj.getClass();
        HashMap fileCache = null;
        f = getClassField(classJarFileFactory, "fileCache");
        if (f == null)
        {
            return;
        }
        try
        {
            obj = f.get(null);
            if (obj instanceof HashMap)
            {
                fileCache = (HashMap) obj;
            }
        }
        catch (IllegalAccessException ex)
        {
        }
        HashMap urlCache = null;
        f = getClassField(classJarFileFactory, "urlCache");
        if (f == null)
        {
            return;
        }
        try
        {
            obj = f.get(null);
            if (obj instanceof HashMap)
            {
                urlCache = (HashMap) obj;
            }
        }
        catch (IllegalAccessException ex)
        {
        }

        if (urlCache != null)
        {
            HashMap urlCacheTmp = (HashMap) urlCache.clone();
            Iterator it = urlCacheTmp.keySet().iterator();
            while (it.hasNext())
            {
                obj = it.next();
                if (!(obj instanceof JarFile))
                {
                    continue;
                }
                JarFile jarFile = (JarFile) obj;
                if (leakedJarNames.contains(jarFile.getName()))
                {
                    try
                    {
                        jarFile.close();
                    }
                    catch (IOException ex)
                    {
                    }
                    if (fileCache != null)
                    {
                        fileCache.remove(urlCache.get(jarFile));
                    }
                    urlCache.remove(jarFile);
                }
            }
        }
        else if (fileCache != null)
        {
            HashMap fileCacheTmp = (HashMap) fileCache.clone();
            Iterator it = fileCacheTmp.keySet().iterator();
            while (it.hasNext())
            {
                Object key = it.next();
                obj = fileCache.get(key);
                if (!(obj instanceof JarFile))
                {
                    continue;
                }
                JarFile jarFile = (JarFile) obj;
                if (leakedJarNames.contains(jarFile.getName()))
                {
                    try
                    {
                        jarFile.close();
                    }
                    catch (IOException ex)
                    {
                    }
                    fileCache.remove(key);
                }
            }
        }
        leakedJarNames.clear();
    }
}