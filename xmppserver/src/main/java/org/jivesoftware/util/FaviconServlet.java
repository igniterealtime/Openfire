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

package org.jivesoftware.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet that gets favicons of webservers and includes them in HTTP responses. This
 * servlet can be used when getting a favicon can take some time so pages can use this
 * servlet as the image source to let the page load quickly and get the favicon images
 * as they are available.<p>
 *
 * This servlet expects the web application to have the {@code images/server_16x16.gif}
 * file that is used when no favicon is found.
 *
 * @author Gaston Dombiak
 */
public class FaviconServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaviconServlet.class);

    /**
     * The content-type of the images to return.
     */
    private static final String CONTENT_TYPE = "image/x-icon";
    /**
     * Bytes of the default favicon to return when one was not found on a host.
     */
    private byte[] defaultBytes;
    /**
     * Pool of HTTP connections to use to get the favicons
     */
    private CloseableHttpClient client;
    /**
     * Cache the domains that a favicon was not found.
     */
    private Cache<String, Integer> missesCache;
    /**
     * Cache the favicons that we've found.
     */
    private Cache<String, byte[]> hitsCache;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        // Create a pool of HTTP connections to use to get the favicons
        client = HttpClientBuilder.create()
            .setConnectionManager(new PoolingHttpClientConnectionManager())
            .setRedirectStrategy(new LaxRedirectStrategy())
            .build();
        // Load the default favicon to use when no favicon was found of a remote host
        try {
           defaultBytes = Files.readAllBytes(Paths.get(JiveGlobals.getHomeDirectory(), "plugins/admin/webapp/images/server_16x16.gif"));
        }
        catch (final IOException e) {
            LOGGER.warn("Unable to retrieve default favicon", e);
        }
        // Initialize caches.
        missesCache = CacheFactory.createCache("Favicon Misses");
        hitsCache = CacheFactory.createCache("Favicon Hits");
    }

    @Override
    public void destroy() {
        try {
            client.close();
        } catch (IOException e) {
            LOGGER.warn("Unable to close HTTP client", e);
        }
    }

    /**
     * Retrieve the image based on it's name.
     *
     * @param request the httpservletrequest.
     * @param response the httpservletresponse.
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) {
        final String host = request.getParameter("host");

        // Validate that we're connected to the host
        final SessionManager sessionManager = SessionManager.getInstance();
        final Optional<String> optionalHost = Stream
            .concat(sessionManager.getIncomingServers().stream(), sessionManager.getOutgoingServers().stream())
            .filter(remoteServerHost -> remoteServerHost.equalsIgnoreCase(host))
            .findAny();
        if (!optionalHost.isPresent()) {
            LOGGER.info("Request to unconnected host {} ignored - using default response", host);
            writeBytesToStream(defaultBytes, response);
            return;
        }

        // Check special cases where we need to change host to get a favicon
        final String hostToUse = "gmail.com".equals(host) ? "google.com" : host;
        byte[] bytes = getImage(hostToUse, defaultBytes);
        if (bytes != null) {
            writeBytesToStream(bytes, response);
        }
    }

    /**
     * Writes out a <code>byte</code> to the ServletOuputStream.
     *
     * @param bytes the bytes to write to the <code>ServletOutputStream</code>.
     */
    private void writeBytesToStream(byte[] bytes, HttpServletResponse response) {
        response.setContentType(CONTENT_TYPE);

        // Send image
        try (ServletOutputStream sos = response.getOutputStream()) {
            sos.write(bytes);
            sos.flush();
        }
        catch (IOException e) {
            // Do nothing
        }
    }

    /**
     * Returns the favicon image bytes of the specified host.
     *
     * @param host the name of the host to get its favicon.
     * @return the image bytes found, otherwise null.
     */
    private byte[] getImage(String host, byte[] defaultImage) {
        // If we've already attempted to get the favicon twice and failed,
        // return the default image.
        if (missesCache.get(host) != null && missesCache.get(host) > 1) {
            // Domain does not have a favicon so return default icon
            return defaultImage;
        }
        // See if we've cached the favicon.
        if (hitsCache.containsKey(host)) {
            return hitsCache.get(host);
        }
        byte[] bytes = getImage("http://" + host + "/favicon.ico");
        if (bytes == null) {
            // Cache that the requested domain does not have a favicon. Check if this
            // is the first cache miss or the second.
            if (missesCache.get(host) != null) {
                missesCache.put(host, 2);
            }
            else {
                missesCache.put(host, 1);
            }
            // Return byte of default icon
            bytes = defaultImage;
        }
        // Cache the favicon.
        else {
            hitsCache.put(host, bytes);
        }
        return bytes;
    }

    private byte[] getImage(String url) {
        // Try to get the favicon from the url using an HTTP connection from the pool
        // that also allows to configure timeout values (e.g. connect and get data)
        final RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(5000)
            .setSocketTimeout(5000)
            .build();
        final HttpUriRequest getRequest = RequestBuilder.get(url)
            .setConfig(requestConfig)
            .build();

        try(final CloseableHttpResponse response = client.execute(getRequest)) {
            if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                final byte[] result = EntityUtils.toByteArray(response.getEntity());

                // Prevent SSRF by checking result (OF-1885)
                if ( !GraphicsUtils.isImage( result ) ) {
                    LOGGER.info( "Ignoring response to an HTTP request that should have returned an image (but returned something else): {}", url) ;
                    return null;
                }
                return result;
            }
        } catch (final IOException ex) {
            LOGGER.debug( "An exception occurred while trying to obtain an image from: {}", url, ex );
        }

        return null;
    }

}
