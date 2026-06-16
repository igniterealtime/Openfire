/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2026 Ignite Realtime Foundation. All rights reserved.
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

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

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
     * Cache of favicons. A present value holds the favicon bytes; an absent value
     * records that the host was checked and has no (usable) favicon.
     */
    private Cache<String, CacheableOptional<byte[]>> faviconCache;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        // Create a pool of HTTP connections to use to get the favicons
        client = HttpClientBuilder.create()
            .setConnectionManager(new PoolingHttpClientConnectionManager())
            .disableRedirectHandling()
            .build();
        // Load the default favicon to use when no favicon was found of a remote host
        try {
            defaultBytes = Files.readAllBytes(JiveGlobals.getHomePath().resolve("plugins").resolve("admin").resolve("webapp").resolve("images").resolve("server_16x16.gif"));
        }
        catch (final IOException e) {
            LOGGER.warn("Unable to retrieve default favicon", e);
        }
        // Initialize cache.
        faviconCache = CacheFactory.createCache("Favicon");
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

        // OF-1885: Ensure that the provided value is a valid hostname.
        if (host == null || (!InetAddresses.isInetAddress(host) && !InternetDomainName.isValid(host))) {
            LOGGER.info("Request for favicon of hostname that can't be parsed as a valid hostname '{}' is ignored.", host);
            if (defaultBytes != null) {
                writeBytesToStream(defaultBytes, response);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
            return;
        }

        // Validate that we're connected to the host
        if (!isConnectedHost(host)) {
            LOGGER.info("Request to unconnected host {} ignored - using default response", host);
            if (defaultBytes != null) {
                writeBytesToStream(defaultBytes, response);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
            return;
        }

        // Check special cases where we need to change host to get a favicon
        final String hostToUse = "gmail.com".equals(host) ? "google.com" : host;
        byte[] bytes = getImage(hostToUse, defaultBytes);
        if (bytes != null) {
            writeBytesToStream(bytes, response);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * Returns true if the supplied host is a server to which Openfire currently has an (incoming or outgoing)
     * server-to-server connection.
     */
    private boolean isConnectedHost(@Nonnull final String host) {
        final SessionManager sessionManager = SessionManager.getInstance();
        return Stream
            .concat(sessionManager.getIncomingServers().stream(), sessionManager.getOutgoingServers().stream())
            .anyMatch(remoteServerHost -> remoteServerHost.equalsIgnoreCase(host));
    }

    /**
     * Writes out a <code>byte</code> to the ServletOuputStream.
     *
     * @param bytes the bytes to write to the <code>ServletOutputStream</code>.
     */
    private void writeBytesToStream(@Nonnull byte[] bytes, @Nonnull HttpServletResponse response) {
        response.setContentType(CONTENT_TYPE);
        response.setHeader("Cache-Control", "public, max-age=86400");
        // Send image
        try (ServletOutputStream sos = response.getOutputStream()) {
            sos.write(bytes);
            sos.flush();
        }
        catch (IOException e) {
            // Do nothing
            LOGGER.trace("Unable to write favicon to response", e);
        }
    }

    /**
     * Returns the favicon image bytes of the specified host.
     *
     * @param host the name of the host to get its favicon.
     * @return the image bytes found, otherwise null.
     */
    private byte[] getImage(@Nonnull String host, byte[] defaultImage) {
        final CacheableOptional<byte[]> cached = faviconCache.get(host);
        if (cached != null) {
            return cached.isPresent() ? cached.get() : defaultImage;
        }
        final byte[] bytes = getImage(host);
        faviconCache.put(host, CacheableOptional.of(bytes));
        return bytes != null ? bytes : defaultImage;
    }

    private byte[] getImage(@Nonnull final String host) {
        final Set<URI> urls = new HashSet<>();

        try {
            // Using a builder to reduce the impact of using user-provided values to generate a URL request.
            urls.add(new URIBuilder().setScheme("https").setHost(host).setPath("favicon.ico").build());
            urls.add(new URIBuilder().setScheme("http").setHost(host).setPath("favicon.ico").build());
        } catch (URISyntaxException e) {
            LOGGER.debug("An exception occurred while trying to obtain an image from: {}", host, e);
            return null;
        }
        // Try to get the favicon from the url using an HTTP connection from the pool
        // that also allows configuring timeout values (e.g. connect and get data)
        final RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(5000)
            .setSocketTimeout(5000)
            .build();

        for (final URI startUrl : urls) {
            URI url = startUrl;
            int redirectsRemaining = 5; // Prevent infinite loops.

            while (url != null && redirectsRemaining-- > 0) {
                final HttpUriRequest getRequest = RequestBuilder.get(url)
                    .setConfig(requestConfig)
                    .build();

                try (final CloseableHttpResponse response = client.execute(getRequest)) {
                    final int status = response.getStatusLine().getStatusCode();

                    if (status == HttpStatus.SC_OK) {
                        final byte[] result = EntityUtils.toByteArray(response.getEntity());
                        if (!GraphicsUtils.isImage(result)) {
                            LOGGER.info("Ignoring response to an HTTP request that should have returned an image (but returned something else): {}", url);
                            break;
                        }
                        return result;
                    }

                    if (status >= 300 && status < 400) {
                        final Header location = response.getFirstHeader("Location");
                        if (location == null) {
                            break;
                        }
                        // Resolve relative redirects against the current URL.
                        // Rather than using LaxRedirectStrategy, re-validate redirect targets to prevent SSRF (OF-3315)
                        final URI target = url.resolve(location.getValue());
                        if (!"http".equalsIgnoreCase(target.getScheme()) && !"https".equalsIgnoreCase(target.getScheme())) {
                            break;
                        }
                        final String targetHost = target.getHost();
                        if (targetHost == null || !isConnectedHost(targetHost)) {
                            LOGGER.info("Ignoring redirect to non-connected or unparseable host: {}", location.getValue());
                            break;
                        }
                        url = target;
                        continue;
                    }

                    // Any other status: give up on this URL.
                    break;
                } catch (final IOException ex) {
                    LOGGER.debug("An exception occurred while trying to obtain an image from: {}", url, ex);
                    break;
                }
            }
        }
        return null;
    }

}
