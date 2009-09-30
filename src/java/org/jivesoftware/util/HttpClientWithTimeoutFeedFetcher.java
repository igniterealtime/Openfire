/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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

/*
 * Copyright 2004 Sun Microsystems, Inc.
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
 *
 */

package org.jivesoftware.util;

import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.fetcher.FetcherEvent;
import com.sun.syndication.fetcher.FetcherException;
import com.sun.syndication.fetcher.impl.AbstractFeedFetcher;
import com.sun.syndication.fetcher.impl.FeedFetcherCache;
import com.sun.syndication.fetcher.impl.SyndFeedInfo;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jivesoftware.openfire.XMPPServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.GZIPInputStream;

/**
 * Feed fetcher implementation that times out the HTTP connection after 3 seconds
 * which fixes a bug where users of the admin console who installed Clearspace
 * behind a proxy server would have to wait upwards of 5 minutes in order for the
 * HTTP connection to jivesoftware.com/blog/feed to timeout.
 * <p/>
 * See <a href="http://www.jivesoftware.com/issues/browse/CS-669">http://www.jivesoftware.com/issues/browse/CS-669</a>
 *
 */
public class HttpClientWithTimeoutFeedFetcher extends AbstractFeedFetcher {

	private FeedFetcherCache feedInfoCache;
    private CredentialSupplier credentialSupplier;

	public HttpClientWithTimeoutFeedFetcher() {
		super();
	}

	/**
	 * @param cache
	 */
	public HttpClientWithTimeoutFeedFetcher(FeedFetcherCache cache) {
		this();
		setFeedInfoCache(cache);
	}


	public HttpClientWithTimeoutFeedFetcher(FeedFetcherCache cache, CredentialSupplier credentialSupplier) {
	    this(cache);
	    setCredentialSupplier(credentialSupplier);
	}

	/**
	 * @return the feedInfoCache.
	 */
	public synchronized FeedFetcherCache getFeedInfoCache() {
		return feedInfoCache;
	}

    /**
	 * @param feedInfoCache the feedInfoCache to set
	 */
	public synchronized void setFeedInfoCache(FeedFetcherCache feedInfoCache) {
		this.feedInfoCache = feedInfoCache;
	}

	/**
     * @return Returns the credentialSupplier.
     */
    public synchronized CredentialSupplier getCredentialSupplier() {
        return credentialSupplier;
    }
    /**
     * @param credentialSupplier The credentialSupplier to set.
     */
    public synchronized void setCredentialSupplier(CredentialSupplier credentialSupplier) {
        this.credentialSupplier = credentialSupplier;
    }

	/**
	 * @see com.sun.syndication.fetcher.FeedFetcher#retrieveFeed(java.net.URL)
	 */
	public SyndFeed retrieveFeed(URL feedUrl) throws IllegalArgumentException, IOException, FeedException, FetcherException {
		if (feedUrl == null) {
			throw new IllegalArgumentException("null is not a valid URL");
		}
		// TODO Fix this
		//System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
		HttpClient client = new HttpClient();
        HttpConnectionManager conManager = client.getHttpConnectionManager();
        conManager.getParams().setParameter("http.connection.timeout", 3000);
        conManager.getParams().setParameter("http.socket.timeout", 3000);

        if (getCredentialSupplier() != null) {
			client.getState().setAuthenticationPreemptive(true);
			// TODO what should realm be here?
			Credentials credentials = getCredentialSupplier().getCredentials(null, feedUrl.getHost());
			if (credentials != null) {
			    client.getState().setCredentials(null, feedUrl.getHost(), credentials);
			}
		}


        System.setProperty("httpclient.useragent", "Openfire Admin Console: v" +
                XMPPServer.getInstance().getServerInfo().getVersion().getVersionString());
        String urlStr = feedUrl.toString();
		FeedFetcherCache cache = getFeedInfoCache();
		if (cache != null) {
			// retrieve feed
			HttpMethod method = new GetMethod(urlStr);
			method.addRequestHeader("Accept-Encoding", "gzip");
			try {
				if (isUsingDeltaEncoding()) {
				    method.setRequestHeader("A-IM", "feed");
				}

				// get the feed info from the cache
			    // Note that syndFeedInfo will be null if it is not in the cache
				SyndFeedInfo syndFeedInfo = cache.getFeedInfo(feedUrl);
			    if (syndFeedInfo != null) {
				    method.setRequestHeader("If-None-Match", syndFeedInfo.getETag());

				    if (syndFeedInfo.getLastModified() instanceof String) {
				        method.setRequestHeader("If-Modified-Since", (String)syndFeedInfo.getLastModified());
				    }
			    }

			    method.setFollowRedirects(true);

				int statusCode = client.executeMethod(method);
				fireEvent(FetcherEvent.EVENT_TYPE_FEED_POLLED, urlStr);
				handleErrorCodes(statusCode);

			    SyndFeed feed = getFeed(syndFeedInfo, urlStr, method, statusCode);

				syndFeedInfo = buildSyndFeedInfo(feedUrl, urlStr, method, feed, statusCode);

				cache.setFeedInfo(new URL(urlStr), syndFeedInfo);

				// the feed may have been modified to pick up cached values
				// (eg - for delta encoding)
				feed = syndFeedInfo.getSyndFeed();

				return feed;
			} finally {
				method.releaseConnection();
			}

		} else {
		    // cache is not in use
			HttpMethod method = new GetMethod(urlStr);
			try {
			    method.setFollowRedirects(true);

				int statusCode = client.executeMethod(method);
				fireEvent(FetcherEvent.EVENT_TYPE_FEED_POLLED, urlStr);
				handleErrorCodes(statusCode);

				return getFeed(null, urlStr, method, statusCode);
			} finally {
				method.releaseConnection();
			}
		}
	}


	/**
     * @param feedUrl
     * @param urlStr
     * @param method
     * @param feed
     * @return
     * @throws MalformedURLException
     */
    private SyndFeedInfo buildSyndFeedInfo(URL feedUrl, String urlStr, HttpMethod method, SyndFeed feed, int statusCode) throws MalformedURLException {
        SyndFeedInfo syndFeedInfo;
        syndFeedInfo = new SyndFeedInfo();

        // this may be different to feedURL because of 3XX redirects
        syndFeedInfo.setUrl(new URL(urlStr));
        syndFeedInfo.setId(feedUrl.toString());

        Header imHeader = method.getResponseHeader("IM");
        if (imHeader != null && imHeader.getValue().indexOf("feed") >= 0 && isUsingDeltaEncoding()) {
			FeedFetcherCache cache = getFeedInfoCache();
			if (cache != null && statusCode == 226) {
			    // client is setup to use http delta encoding and the server supports it and has returned a delta encoded response
			    // This response only includes new items
			    SyndFeedInfo cachedInfo = cache.getFeedInfo(feedUrl);
			    if (cachedInfo != null) {
				    SyndFeed cachedFeed = cachedInfo.getSyndFeed();

				    // set the new feed to be the orginal feed plus the new items
				    feed = combineFeeds(cachedFeed, feed);
			    }
			}
		}

        Header lastModifiedHeader = method.getResponseHeader("Last-Modified");
        if (lastModifiedHeader != null) {
            syndFeedInfo.setLastModified(lastModifiedHeader.getValue());
        }

        Header eTagHeader = method.getResponseHeader("ETag");
        if (eTagHeader != null) {
            syndFeedInfo.setETag(eTagHeader.getValue());
        }

        syndFeedInfo.setSyndFeed(feed);

        return syndFeedInfo;
    }

    /**
	 * @param urlStr
	 * @param method
	 * @return
	 * @throws IOException
	 * @throws HttpException
	 * @throws FetcherException
	 * @throws FeedException
	 */
	private static SyndFeed retrieveFeed(String urlStr, HttpMethod method) throws IOException, HttpException, FetcherException, FeedException {

		InputStream stream = null;
		if ((method.getResponseHeader("Content-Encoding") != null) && ("gzip".equalsIgnoreCase(method.getResponseHeader("Content-Encoding").getValue()))) {
		    stream = new GZIPInputStream(method.getResponseBodyAsStream());
		} else {
		    stream = method.getResponseBodyAsStream();
		}
		try {
		    XmlReader reader = null;
		    if (method.getResponseHeader("Content-Type") != null) {
		        reader = new XmlReader(stream, method.getResponseHeader("Content-Type").getValue(), true);
		    } else {
		        reader = new XmlReader(stream, true);
		    }
			return new SyndFeedInput().build(reader);
		} finally {
		    if (stream != null) {
		        stream.close();
		    }
		}
	}

	private SyndFeed getFeed(SyndFeedInfo syndFeedInfo, String urlStr, HttpMethod method, int statusCode) throws IOException, HttpException, FetcherException, FeedException {

		if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED && syndFeedInfo != null) {
		    fireEvent(FetcherEvent.EVENT_TYPE_FEED_UNCHANGED, urlStr);
		    return syndFeedInfo.getSyndFeed();
		}

		SyndFeed feed = retrieveFeed(urlStr, method);
		fireEvent(FetcherEvent.EVENT_TYPE_FEED_RETRIEVED, urlStr, feed);
		return feed;
	}

    public interface CredentialSupplier {
        public Credentials getCredentials(String realm, String host);
    }


}
