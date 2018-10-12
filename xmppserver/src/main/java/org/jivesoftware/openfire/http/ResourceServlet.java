/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.http;

import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Combines and serves resources, such as javascript or css files.
 */
public class ResourceServlet extends HttpServlet {

    private static final Logger Log = LoggerFactory.getLogger(ResourceServlet.class);

    //    private static String suffix = "";    // Set to "_src" to use source version
    private static long expiresOffset = 3600 * 24 * 10;	// 10 days util client cache expires
    private boolean debug = false;
    private boolean disableCompression = false;
    private static Cache<String, byte[]> cache = CacheFactory.createCache("Javascript Cache");

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        debug = Boolean.valueOf(config.getInitParameter("debug"));
        disableCompression = Boolean.valueOf(config.getInitParameter("disableCompression"));
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) {
        boolean compress = false;

        boolean javascript = request.getRequestURI().endsWith("scripts/");

        if (!disableCompression) {
            if (request.getHeader("accept-encoding") != null &&
                    request.getHeader("accept-encoding").contains("gzip")) {
                compress = true;
            }
            else if (request.getHeader("---------------") != null) {
                // norton internet security
                compress = true;
            }
        }

        if(javascript) {
            response.setHeader("Content-type", "text/javascript");
        }
        else {
            response.setHeader("Content-type", "text/css");
        }
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

        try {
            byte[] content;
            String cacheKey = String.valueOf(compress + " " + javascript);
            content = cache.get(cacheKey);
            if (javascript && (debug || content == null)) {
                content = getJavaScriptContent(compress);
                cache.put(cacheKey, content);
            }
            else if(!javascript && content == null) {

            }

            response.setContentLength(content.length);
            if (compress) {
                response.setHeader("Content-Encoding", "gzip");
            }

            // Write the content out
            try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
                try (OutputStream out = response.getOutputStream()) {

                    // Use a 128K buffer.
                    byte[] buf = new byte[128 * 1024];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                    out.flush();
                }
            }
        }
        catch (IOException e) {
            Log.error(e.getMessage(), e);
        }
    }

    private static byte[] getJavaScriptContent(boolean compress) throws IOException
    {
        StringWriter writer = new StringWriter();
        for(String file : getJavascriptFiles()) {
            writer.write(getJavaScriptFile(file));
        }
        
        if (compress) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                    gzos.write(writer.toString().getBytes());
                    gzos.finish();
                    gzos.flush();
                    return baos.toByteArray();
                }
            }
        }
        else {
            return writer.toString().getBytes();
        }
    }

    private static Collection<String> getJavascriptFiles() {
        return Arrays.asList("prototype.js", "getelementsbyselector.js", "sarissa.js",
                "connection.js", "yahoo-min.js", "dom-min.js", "event-min.js", "dragdrop-min.js",
                "yui-ext.js", "spank.js");
    }

    private static String getJavaScriptFile(String path) {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getResourceAsStream(path)) {
            if (in == null) {
                Log.error("Unable to find javascript file: '" + path + "' in classpath");
                return "";
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line.trim()).append('\n');
                }
            }
        } catch (Exception e) {
            Log.error("Error loading JavaScript file: '" + path + "'.", e);
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
