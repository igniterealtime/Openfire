/*
 * @(#)URLUtils.java
 *
 * Copyright 2003-2004 by Jive Software,
 * 135 W 29th St, Suite 802, New York, NY 10001,  U.S.A.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Jive Software.
 */
package org.jivesoftware.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * <code>URLUtils</code> class handles most cases when using URL's.
 *
 * @author Derek DeMoro
 * @version 1.0, 04/21/2004
 */

public class URLUtils {

    private URLUtils() {
    }

    /**
     * Copy a give inputStream to given outputstream.
     */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        final byte[] buffer = new byte[4096];
        while (true) {
            final int bytesRead = in.read(buffer);
            if (bytesRead < 0) {
                break;
            }
            out.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Returns a suffix(if any) of a url.
     *
     * @param url the url to retrieve the suffix from.
     * @return suffix of the given url, null if no suffix is found.
     */
    public static String getSuffix(URL url) {
        final String path = url.getPath();
        int lastDot = path.lastIndexOf('.');

        return (lastDot >= 0) ? path.substring(lastDot) : "";
    }


    /**
     * Copies the contents at <CODE>source</CODE> to <CODE>destination</CODE>.
     */
    public static void copyURL(URL source, File destination) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = source.openStream();
            out = new FileOutputStream(destination);
            destination.mkdirs();
            copy(in, out);
        }
        finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            }
            catch (IOException e) {
            }
        }
    }


    /**
     * Returns the canonical form of the <code>URL</code>
     *
     * @param url the url to retrieve the canoncial form from.
     * @return the canoncical form of a url.
     */
    public URL canonicalize(URL url) {
        return url;
    }

    /**
     * Checks to see if the URL can be read.
     *
     * @param url the url to read from.
     * @return true if the URL can be read, false otherwise.
     */
    public boolean canRead(URL url) {
        try {
            final URLConnection urlConnection = url.openConnection();
            return urlConnection.getDoInput();
        }
        catch (Exception e) {
            return false;
        }
    }


    /**
     * Checks to see if the URL can be written to.
     *
     * @param url the url to write to.
     * @return true if the url can be written to.
     */
    public boolean canWrite(URL url) {
        try {
            final URLConnection urlConnection = url.openConnection();
            return urlConnection.getDoOutput();
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks to see if the resource at the given url can be created.
     *
     * @param url the url to check if creation is possible.
     * @return true if the resource can be created.
     */
    public boolean canCreate(URL url) {
        return true;
    }

    /**
     * Tests to see if the URL is valid.
     *
     * @param url the url to test.
     * @return true if the url is valid.
     */
    public boolean isValid(URL url) {
        if (exists(url)) {
            return true;
        }

        return canCreate(url);
    }

    /**
     * Tests to see if the resource at the given <code>URL</code> is valid.
     *
     * @param url the url to check.
     * @return true if the resource at the given <code>URL</code> exists.
     */
    public static boolean exists(URL url) {
        return toFile(url).exists();
    }

    /**
     * Creates directory(s) at the given <code>URL</code>
     *
     * @param url the url where the directory(s) should be made.
     * @return true if the directory(s) were created.
     */
    public static boolean mkdirs(URL url) {
        final File file = toFile(url);
        if (!file.exists()) {
            return file.mkdirs();
        }
        return true;
    }


    /**
     * Returns the name of the resource at a given <code>URL</code>
     *
     * @param url the url.
     * @return the filename of the url.
     */
    public static String getFileName(URL url) {
        if (url == null) {
            return "";
        }

        final String path = url.getPath();
        if (path.equals("/")) {
            return "/";
        }
        final int lastSep = path.lastIndexOf('/');
        if (lastSep == path.length() - 1) {
            final int lastSep2 = path.lastIndexOf('/', lastSep - 1);
            return path.substring(lastSep2 + 1, lastSep);
        }
        else {
            return path.substring(lastSep + 1);
        }
    }


    /**
     * Returns the numbers of bytes in the resource identified by
     * the given <code>URL</code>
     *
     * @param url the url of the resource.
     * @return the length in bytes of the resource.
     */
    public long getLength(URL url) {
        try {
            final URLConnection urlConnection = url.openConnection();
            return urlConnection.getContentLength();
        }
        catch (Exception e) {
            return -1;
        }
    }


    /**
     * This creates a valid path by converting file sepeartor to forward slases.
     */
    public static String createValidPath(String path) {
        if (File.separatorChar != '/') {
            path = path.replace(File.separatorChar, '/');
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    public static final File toFile(URL url) {
        final String path = url.getPath();
        final File file = new File(path);
        return file;
    }

    public static URL getParent(URL url) {
        final File file = toFile(url);
        final File parentFile = file.getParentFile();
        if (parentFile != null && !file.equals(parentFile)) {
            try {
                return parentFile.toURL();
            }
            catch (Exception ex) {
                return null;
            }
        }
        return null;
    }


}
