/**
 * $Revision $
 * $Date $
 *
 * Copyright (C) 2005-2010 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin.ofsocial;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tuckey.web.filters.urlrewrite.extend.RewriteMatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom match class to handle processing of WordPress clean URLs that match the
 * criteria for rewriting.
 *
 */
public class WpMatch extends RewriteMatch {
    private static final Logger Log = LoggerFactory.getLogger(WpMatch.class);

	/**
	 * Do the actual rewrite.  Request URI in the form "/node/3" would be rewritten
	 * to "/index.php?q=node/3" and then forwarded.
	 */
	public boolean execute(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String queryString = request.getQueryString();
    	// Do the rewrite

		StringBuilder newURI = new StringBuilder(512);

		if (request.getRequestURI().indexOf("/events/") > -1)
		{
			newURI.append("/index.php?post_type=ep_event&q=").append(request.getRequestURI().substring(1));

		} else {

			newURI.append("/index.php?q=").append(request.getRequestURI().substring(1));
		}

		if (queryString != null) {

			newURI.append("&").append(request.getQueryString());
		}
		Log.debug("changes = " + newURI.toString());

    	RequestDispatcher rd = request.getRequestDispatcher(newURI.toString());
    	rd.forward(request, response);

		return true;
	}
}
