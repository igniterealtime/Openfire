/**
 * $RCSfile$
 * $Revision: 18470 $
 * $Date: 2005-02-15 19:34:51 -0800 (Tue, 15 Feb 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.utils;

import org.xmpp.component.ComponentManagerFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;

/**
 * <code>URLFileSystem</code> class handles some of the most common
 * functionallity when working with URLs.
 *
 * @version 1.0, 03/12/14
 */

public class URLFileSystem {
    public static void main(String args[]) {
    }

    /**
     * Copies the contents at <CODE>src</CODE> to <CODE>dst</CODE>.
     */
    public static void copy(URL src, File dst) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = src.openStream();
            out = new FileOutputStream(dst);
            dst.mkdirs();
            copy(in, out);
        }
        finally {
            try {
                if (in != null) in.close();
            }
            catch (IOException e) {
            }
            try {
                if (out != null) out.close();
            }
            catch (IOException e) {
            }
        }
    }

    /**
     * Common code for copy routines.  By convention, the streams are
     * closed in the same method in which they were opened.  Thus,
     * this method does not close the streams when the copying is done.
     */
    private static void copy(InputStream in, OutputStream out)
            throws IOException {
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
     * If a dot ('.') occurs in the path portion of the {@link URL}, then
     * all of the text starting at the last dot is returned, including
     * the dot.  If the last dot is also the last character in the path,
     * then the dot by itself is returned.  If there is no dot in the
     * path, then the empty string is returned.
     */
    public static String getSuffix(URL url) {
        final String path = url.getPath();
        int lastDot = path.lastIndexOf('.');

        return (lastDot >= 0) ? path.substring(lastDot) : "";
    }


    //--------------------------------------------------------------------------
    //  URLFileSystemHelper public API...
    //--------------------------------------------------------------------------
    /**
     * Returns a canonical form of the {@link URL}, if one is available.
     * <P>
     * <p/>
     * The default implementation just returns the specified {@link URL}
     * as-is.
     */
    public URL canonicalize(URL url) {
        return url;
    }

    /**
     * Tests whether the application can read the resource at the
     * specified {@link URL}.
     *
     * @return <CODE>true</CODE> if and only if the specified
     *         {@link URL} points to a resource that exists <EM>and</EM> can be
     *         read by the application; <CODE>false</CODE> otherwise.
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
     * Tests whether the application can modify the resource at the
     * specified {@link URL}.
     *
     * @return <CODE>true</CODE> if and only if the specified
     *         {@link URL} points to a file that exists <EM>and</EM> the
     *         application is allowed to write to the file; <CODE>false</CODE>
     *         otherwise.
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
     * Tests whether the application can create the resource at the specified
     * {@link URL}.
     *
     * @return <CODE>true</CODE> if the resource at the specified {@link URL}
     *         exists or can be created; <CODE>false</CODE> otherwise.
     */
    public boolean canCreate(URL url) {
        return true;
    }

    /**
     * Tests whether the specified {@link URL} is valid. If the resource
     * pointed by the {@link URL} exists the method returns <CODE>true</CODE>.
     * If the resource does not exist, the method tests that all components
     * of the path can be created.
     *
     * @return <CODE>true</CODE> if the {@link URL} is valid.
     */
    public boolean isValid(URL url) {
        if (exists(url)) {
            return true;
        }

        return canCreate(url);
    }

    /**
     * Returns <CODE>true</CODE> if the specified {@link URL} points to a
     * resource that currently exists; returns <CODE>false</CODE>
     * otherwise.<P>
     * <p/>
     * The default implementation simply returns <CODE>false</CODE>
     * without doing anything.
     */
    public static boolean exists(URL url) {
        return url2File(url).exists();
    }

    public static boolean mkdirs(URL url) {
        final File file = url2File(url);
        if (!file.exists()) {
            return file.mkdirs();
        }
        return true;
    }


    /**
     * Returns the name of the file contained by the {@link URL}, not
     * including any protocol, hostname authentication, directory path,
     * anchor, or query.  This simply returns the simple filename.  For
     * example, if you pass in an {@link URL} whose string representation
     * is:
     * <p/>
     * <BLOCKQUOTE><CODE>
     * protocol://host:1010/dir1/dir2/file.ext#anchor?query
     * </CODE></BLOCKQUOTE>
     * <p/>
     * the returned value is "<CODE>file.ext</CODE>" (without the
     * quotes).<P>
     * <p/>
     * The returned file name should only be used for display purposes
     * and not for opening streams or otherwise trying to locate the
     * resource indicated by the {@link URL}.
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
     * Returns the number of bytes contained in the resource that the
     * specified {@link URL} points to.  If the length cannot be
     * determined, <CODE>-1</CODE> is returned.<P>
     * <p/>
     * The default implementation attempts to get the content length from
     * the {@link URLConnection} associated with the {@link URL}.  If that
     * fails for some reason (e.g. the resource does not exist, there was
     * some other an I/O exception, etc.), <CODE>-1</CODE> is returned.
     *
     * @see URLConnection
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
     * Returns the name of the file contained by the {@link URL}, not
     * including any protocol, hostname authentication, directory path,
     * anchor, or query.  This simply returns the simple filename.  For
     * example, if you pass in an {@link URL} whose string representation
     * is:
     * <p/>
     * <BLOCKQUOTE><CODE>
     * protocol://host:1010/dir1/dir2/file.ext1.ext2#anchor?query
     * </CODE></BLOCKQUOTE>
     * <p/>
     * the returned value is "<CODE>file</CODE>" (without the quotes).<P>
     * <p/>
     * The returned file name should only be used for display purposes
     * and not for opening streams or otherwise trying to locate the
     * resource indicated by the {@link URL}.<P>
     * <p/>
     * The default implementation first calls {@link #getFileName(URL)} to
     * get the file name part.  Then all characters starting with the
     * first occurrence of '.' are removed.  The remaining string is then
     * returned.
     */
    public static String getName(URL url) {
        final String fileName = getFileName(url);
        final int firstDot = fileName.indexOf('.');
        return firstDot > 0 ? fileName.substring(0, firstDot) : fileName;
    }


    /**
     * Returns the path part of the {@link URL}.  The returned string
     * is acceptable to use in one of the {@link URLFactory} methods
     * that takes a path.<P>
     * <p/>
     * The default implementation delegates to {@link URL#getPath()}.
     */
    public String getPath(URL url) {
        return url.getPath();
    }


    /**
     * Returns the path part of the {@link URL} without the last file
     * extension.  To clarify, the following examples demonstrate the
     * different cases that come up:
     * <p/>
     * <TABLE BORDER COLS=2 WIDTH="100%">
     * <TR>
     * <TD><CENTER>Path part of input {@link URL}</CENTER></TD>
     * <TD><CENTER>Output {@link String}</CENTER</TD>
     * </TR>
     * <TR>
     * <TD><CODE>/dir/file.ext</CODE></TD>
     * <TD><CODE>/dir/file</CODE></TD>
     * <TR>
     * <TR>
     * <TD><CODE>/dir/file.ext1.ext2</CODE></TD>
     * <TD><CODE>/dir/file.ext1</CODE></TD>
     * <TR>
     * <TR>
     * <TD><CODE>/dir1.ext1/dir2.ext2/file.ext1.ext2</CODE></TD>
     * <TD><CODE>/dir1.ext1/dir2.ext2/file.ext1</CODE></TD>
     * <TR>
     * <TR>
     * <TD><CODE>/file.ext</CODE></TD>
     * <TD><CODE>/file</CODE></TD>
     * <TR>
     * <TR>
     * <TD><CODE>/dir.ext/file</CODE></TD>
     * <TD><CODE>/dir.ext/file</CODE></TD>
     * <TR>
     * <TR>
     * <TD><CODE>/dir/file</CODE></TD>
     * <TD><CODE>/dir/file</CODE></TD>
     * <TR>
     * <TR>
     * <TD><CODE>/file</CODE></TD>
     * <TD><CODE>/file</CODE></TD>
     * <TR>
     * <TR>
     * <TD><CODE>/.ext</CODE></TD>
     * <TD><CODE>/</CODE></TD>
     * <TR>
     * </TABLE>
     * <p/>
     * The default implementation gets the path from {@link
     * #getPath(URL)} and then trims off all of the characters beginning
     * with the last "." in the path, if and only if the last "." comes
     * after the last "/" in the path.  If the last "." comes before
     * the last "/" or if there is no "." at all, then the entire path
     * is returned.
     */
    public String getPathNoExt(URL url) {
        final String path = getPath(url);
        final int lastSlash = path.lastIndexOf("/");
        final int lastDot = path.lastIndexOf(".");
        if (lastDot <= lastSlash) {
            //  When the lastDot < lastSlash, it means that one of the
            //  directories has an extension, but the filename itself has
            //  no extension.  In this case, returning the whole path is
            //  the correct behavior.
            //
            //  The only time that lastDot and lastSlash can be equal occurs
            //  when both of them are -1.  In that case, returning the whole
            //  path is the correct behavior.
            return path;
        }
        //  At this point, we know that lastDot must be non-negative, so
        //  we can return the whole path string up to the last dot.
        return path.substring(0, lastDot);
    }


    /**
     * Returns the platform-dependent String representation of the
     * {@link URL}; the returned string should be considered acceptable
     * for users to read.  In general, the returned string should omit
     * as many parts of the {@link URL} as possible.  For the "file"
     * protocol, therefore, the platform pathname should just be the
     * pathname alone (no protocol) using the appropriate file separator
     * character for the current platform.  For other protocols, it may
     * be necessary to reformat the {@link URL} string into a more
     * human-readable form.  That decision is left to each
     * <CODE>URLFileSystemHelper</CODE> implementor.
     * <p/>
     * The default implementation returns <CODE>url.toString()</CODE>.
     * If the {@link URL} is <CODE>null</CODE>, the empty string is
     * returned.
     *
     * @return The path portion of the specified {@link URL} in
     *         platform-dependent notation.  This value should only be used for
     *         display purposes and not for opening streams or otherwise trying
     *         to locate the document.
     */
    public String getPlatformPathName(URL url) {
        return url != null ? url.toString() : "";
    }

    public static URL newFileURL(File file) {
        String filePath = file.getPath();
        if (filePath == null) {
            return null;
        }
        final String path = sanitizePath(filePath);
        return newURL("file", path);
    }

    public static URL newFileURL(String filePath) {
        if (filePath == null) {
            return null;
        }
        final String path = sanitizePath(filePath);
        return newURL("file", path);
    }

    /**
     * This "sanitizes" the specified string path by converting all
     * {@link File#separatorChar} characters to forward slash ('/').
     * Also, a leading forward slash is prepended if the path does
     * not begin with one.
     */
    private static String sanitizePath(String path) {
        if (File.separatorChar != '/') {
            path = path.replace(File.separatorChar, '/');
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    public static URL newURL(String protocol, String path) {
        return newURL(protocol, null, null, -1, path, null, null);
    }

    //--------------------------------------------------------------------------
    //  direct access factory methods...
    //--------------------------------------------------------------------------
    /**
     * Creates a new {@link URL} whose parts have the exact values that
     * are specified.  <EM>In general, you should avoid calling this
     * method directly.</EM><P>
     * <p/>
     * This method is the ultimate place where all of the other
     * <CODE>URLFactory</CODE> methods end up when creating an
     * {@link URL}.
     * <p/>
     * Non-sanitizing.
     */
    public static URL newURL(String protocol, String userinfo,
                             String host, int port,
                             String path, String query, String ref) {
        try {
            final URL seed = new URL(protocol, "", -1, "");
            final String authority = port < 0 ? host : host + ":" + port;
            final Object[] args = new Object[]
            {
                protocol, host, new Integer(port),
                authority, userinfo,
                path, query, ref,
            };

            //  IMPORTANT -- this *MUST* be the only place in URLFactory where
            //  the URL.set(...) method is used.  --jdijamco
            urlSet.invoke(seed, args);
            return seed;
        }
        catch (Exception e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
            return null;
        }
    }

    /**
     * This {@link java.lang.reflect.Method} is used to work-around a bug in Sun's
     * <CODE>java.net.URL</CODE> implementation.  The {@link java.lang.reflect.Method}
     * allows us to set the parts of an {@link URL} directly.
     */
    private static final Method urlSet;

    static {
        final Class str = String.class;
        try {
            urlSet = URL.class.getDeclaredMethod("set", new Class[]{str, str, int.class, str, str, str, str, str});

            //  IMPORTANT:  This call to setAccessible effectively overrides
            //  the "protected" visibility constraint on the URL.set(...)
            //  method.  This is an intentional breaking of encapsulation to
            //  work-around severe bugs in Sun's java.net.URL implementation
            //  having to do with:
            //    *  poor handling of special characters like #, ?, and ;
            //    *  poor handling of whitespace
            //    *  no go way to disambiguate UNC paths on Win32
            //
            //  The use of setAccessible is an implementation detail of the
            //  URLFactory, and if Sun some day fixes their java.net.URL
            //  implementation to address the problems above, we may be able
            //  to change the internal mechanism to use the regular URL
            //  constructors.  For the time being, after having weighed the
            //  various other alternatives and even tried some of them (and
            //  encountered other problems), our decision is to force our way
            //  through to the URL.set(...) method, taking care to invoke
            //  it method exactly once per URL object with the exactly the
            //  right arguments.
            //
            //  --jdijamco  March 14, 2001
            urlSet.setAccessible(true);
        }
        catch (NoSuchMethodException e) {
            //!jdijamco -- Have some fallback option so that <clinit> doesn't
            //!jdijamco -- just totally barf and prevent the IDE from starting?
            throw new IllegalStateException();
        }
    }

    public static final File url2File(URL url) {
        final String path = url.getPath();
        final File file = new File(path);
        return file;
    }

    public static URL getParent(URL url) {
        final File file = url2File(url);
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

