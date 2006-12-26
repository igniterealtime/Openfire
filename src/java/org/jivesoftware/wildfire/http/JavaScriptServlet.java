/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.wildfire.http;

import org.jivesoftware.util.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.io.*;

/**
 *
 */
public class JavaScriptServlet extends HttpServlet {
//    private static String suffix = "";    // Set to "_src" to use source version
    private static long expiresOffset = 3600 * 24 * 10;	// 10 days util client cache expires
    private boolean debug = false;
    private boolean disableCompression = false;
    private static Cache<String, byte[]> cache = CacheManager.initializeCache(
            "Javascript Cache", "javascript", 128 * 1024, expiresOffset);

    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        debug = Boolean.valueOf(config.getInitParameter("debug"));
        disableCompression = Boolean.valueOf(config.getInitParameter("disableCompression"));
    }

    public void service(HttpServletRequest request, HttpServletResponse response) {
        boolean compress = false;

        if (!disableCompression) {
            if (request.getHeader("accept-encoding") != null &&
                request.getHeader("accept-encoding").indexOf("gzip") != -1)
            {
                compress = true;
            }
            else if (request.getHeader("---------------") != null) {
                // norton internet security
                compress = true;
            }
        }

        response.setHeader("Content-type", "text/javascript");
        response.setHeader("Vary", "Accept-Encoding"); // Handle proxies

        if (!debug) {
            DateFormat formatter = new SimpleDateFormat("d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
            formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            response.setHeader("Expires", formatter.format(new Date(System.currentTimeMillis()
                    + expiresOffset)));
            response.setHeader("Cache-Control", "max-age=" + expiresOffset);
        }
        else {
            response.setHeader("Expires", "1");
            compress = false;
        }
        OutputStream out = null;
        InputStream in = null;

        try {
            byte[] jsContent;
            String cacheKey = String.valueOf(compress);
            jsContent = cache.get(cacheKey);
            if (debug || jsContent == null) {
                jsContent = getJavaScriptContent(compress);
                cache.put(cacheKey, jsContent);
            }

            response.setContentLength(jsContent.length);
            if (compress) {
                response.setHeader("Content-Encoding", "gzip");
            }

            // Write the content out
            in = new ByteArrayInputStream(jsContent);
            out = response.getOutputStream();

            // Use a 128K buffer.
            byte[] buf = new byte[128*1024];
            int len;
            while ((len=in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        }
        catch (IOException e) {
            Log.error(e);
        }
        finally {
            try { if (in != null) { in.close(); } } catch (Exception ignored) { /* ignored */ }
            try { if (out != null) { out.close(); } } catch (Exception ignored) { /* ignored */ }
        }
    }

    private static byte[] getJavaScriptContent(boolean compress) throws IOException
    {
        StringWriter writer = new StringWriter();
        for(String file : getJavascriptFiles()) {
            writer.write(getJavaScriptFile(file));
        }
        
        if (compress) {
            ByteArrayOutputStream baos = null;
            GZIPOutputStream gzos = null;
            try {
                baos = new ByteArrayOutputStream();
                gzos = new GZIPOutputStream(baos);

                gzos.write(writer.toString().getBytes());
                gzos.finish();
                gzos.flush();
                gzos.close();

                return baos.toByteArray();
            }
            finally {
                try { if (gzos != null) { gzos.close(); } }
                catch (Exception ignored) { /* ignored */ }
                try { if (baos != null) { baos.close(); } }
                catch (Exception ignored) { /* ignored */ }
            }
        }
        else {
            return writer.toString().getBytes();
        }
    }

    private static Collection<String> getJavascriptFiles() {
        return Arrays.asList("xmlextras.js", "connection.js", "dojo.js",
                "flash.js");
    }

    private static String getJavaScriptFile(String path) {
        StringBuilder sb = new StringBuilder();
        InputStream in = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        try {
            in = getResourceAsStream(path);
            if (in == null) {
                Log.error("Unable to find javascript file: '" + path + "' in classpath");
                return "";
            }

            isr = new InputStreamReader(in, "ISO-8859-1");
            br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line.trim()).append('\n');
            }
        }
        catch (Exception e) {
            Log.error("Error loading JavaScript file: '" + path + "'.", e);
        }
        finally {
            try { if (br != null) { br.close(); } } catch (Exception ignored) { /* ignored */ }
            try { if (isr != null) { isr.close(); } } catch (Exception ignored) { /* ignored */ }
            try { if (in != null) { in.close(); } } catch (Exception ignored) { /* ignored */ }
        }

        return sb.toString();
    }

    private static InputStream getResourceAsStream(String resourceName) {
        File file = new File(JiveGlobals.getHomeDirectory() + File.separator +
                "resources" + File.separator + "spank" + File.separator + "scripts", resourceName);
        try {
            return new FileInputStream(file);
        }
        catch (FileNotFoundException e) {
            return null;
        }
    }
}
