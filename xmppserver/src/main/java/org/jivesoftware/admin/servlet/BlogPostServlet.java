/*
 * Copyright (C) 2023-2024 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.admin.servlet;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jivesoftware.openfire.update.UpdateManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SAXReaderUtil;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import org.json.*;

/**
 * Servlet used for retrieval of IgniteRealtime's blog's RSS feed.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class BlogPostServlet extends HttpServlet {

    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("rss.enabled")
        .setDynamic(true)
        .setDefaultValue(true)
        .addListener((b) -> lastRSSFetch = Instant.EPOCH)
        .build();

    public static final SystemProperty<String> URL = SystemProperty.Builder.ofType(String.class)
        .setKey("rss.url")
        .setDynamic(true)
        .setDefaultValue("https://discourse.igniterealtime.org/c/blogs/ignite-realtime-blogs.rss")
        .addListener((b) -> lastRSSFetch = Instant.EPOCH)
        .build();

    public static final SystemProperty<Duration> REFRESH = SystemProperty.Builder.ofType(Duration.class)
        .setKey("rss.refresh")
        .setDynamic(true)
        .setDefaultValue(Duration.ofHours(6))
        .setChronoUnit(ChronoUnit.MINUTES)
        .addListener((b) -> lastRSSFetch = Instant.EPOCH)
        .build();

    private static final Logger Log = LoggerFactory.getLogger(BlogPostServlet.class);
    private static Instant lastRSSFetch = Instant.EPOCH;
    private JSONArray lastItems = null;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        synchronized (this) {
            if (lastItems == null || Duration.between(lastRSSFetch, Instant.now()).compareTo(REFRESH.getDefaultValue()) > 0) {
                Log.debug("Trying to obtain latest blog posts from IgniteRealtime.org");

                final String proxyHost = UpdateManager.PROXY_HOST.getValue();
                final int proxyPort = UpdateManager.PROXY_PORT.getValue();

                final HttpRoutePlanner routePlanner;
                if (proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0) {
                    routePlanner = new DefaultProxyRoutePlanner(new HttpHost(proxyHost, proxyPort, "http"));
                } else {
                    routePlanner = new DefaultRoutePlanner(null);
                }
                final HttpGet httpGet = new HttpGet(URL.getValue());

                try (final CloseableHttpClient client = HttpClients.custom().setRoutePlanner(routePlanner).build();
                     final CloseableHttpResponse httpResponse = client.execute(httpGet);
                     final InputStream stream = httpResponse.getEntity().getContent()) {

                    lastItems = parseFirstEntries(stream, 7);
                    lastRSSFetch = Instant.now();
                } catch (final Throwable throwable) {
                    Log.warn("Unable to download blogposts from igniterealtime.org", throwable);
                }
            }
        }

        final JSONObject result = new JSONObject();
        result.put("items", lastItems);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(result.toString(2));
    }

    /**
     * Parses the input stream, expected to contain RSS data, into the proprietary format used by the admin console.
     *
     * @param inputStream An input stream of RSS data
     * @param count The maximum number of items to return.
     * @return A collection or RSS items.
     */
    static JSONArray parseFirstEntries(final InputStream inputStream, final int count) throws ExecutionException, InterruptedException
    {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

        final JSONArray result = new JSONArray();

        final Document document = SAXReaderUtil.readDocument(inputStream);
        final List<Node> nodes = document.selectNodes("/rss/channel/item");
        for (final Node node : nodes) {
            if (!(node instanceof Element)) {
                continue;
            }

            try {
                final Element item = (Element) node;

                final JSONObject o = new JSONObject();
                o.put("link", item.elementText("link"));
                o.put("title", item.elementText("title"));
                o.put("date", JiveGlobals.formatDate(dateFormat.parse(item.elementText("pubDate"))));
                result.put(o);
            } catch (Exception e) {
                Log.debug("Unable to parse element as RSS data: {}", node.asXML(), e);
            }

            if (result.length() >= count) {
                break;
            }
        }
        return result;
    }
}
