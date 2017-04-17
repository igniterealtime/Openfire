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

import java.io.File;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.RequestDispatcher;

import org.tuckey.web.filters.urlrewrite.extend.RewriteMatch;
import org.tuckey.web.filters.urlrewrite.extend.RewriteRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom rule class to determine if a request URI meets the WordPress
 * criteria for rewriting.
 *
 */
public class WpRule extends RewriteRule {
    private static final Logger Log = LoggerFactory.getLogger(WpRule.class);

	private ServletContext sc;

	/**
	 * Initialization method - saves the ServletContext object so that
	 * it can be used later to determine the actual filesystem path
	 * to a requested object.
	 *
	 * @param sc The ServletContext object.
	 * @return true
	 */
	public boolean init(ServletContext sc) {
		this.sc = sc;

		return true;
	}


	/**
	 * Performs the actual testing to determine if the request URL is to be rewritten.
	 *
	 * @param request The HttpServletRequest object.
	 * @param response The HttpServletResponse object.
	 * @return RewriteMatch object which is to perform the actual rewrite.
	 */
	public RewriteMatch matches(HttpServletRequest request, HttpServletResponse response) {
		String requestURI = request.getRequestURI();

		if (requestURI.endsWith(".php")) return null;

		Log.debug("matches url " + requestURI + " for " + request.getHeader("Authorization"));

		if (requestURI.indexOf("/ofsocial/chat/") > -1) return null;

		if (requestURI == null) return null;

		if (requestURI.equals("/")) return null;

		if (requestURI.equals("/favicon.ico")) return null;

		if (requestURI.indexOf("/ofsocial/wp-admin/") > -1) return null;

		if (requestURI.indexOf("/ofsocial/wp-includes/") > -1) return null;

  		if (requestURI.indexOf("/ofsocial/wp-content/uploads") > -1)
  		{
			Cookie[] cookies = request.getCookies();
			boolean loggedIn = false;

			if (cookies != null)
			{
			  for (int i = 0; i < cookies.length; i++)
			  {
				if (cookies[i].getName().indexOf("wordpress_logged_in") > -1) {
				  loggedIn = true;;
				  break;
				}
			  }
			}

			if (!loggedIn)
			{
				try {
					RequestDispatcher rd = request.getRequestDispatcher("/wp-login.php?redirect_to=" + request.getRequestURI().substring(1));
					rd.forward(request, response);
				} catch (Exception e) {

					System.err.println("RewriteMatch " + e);
				}
			}

			return null;
		}

		if (requestURI.indexOf("/ofsocial/wp-content/") > -1) return null;

		// No rewrite if real path cannot be obtained, or if request URI points to a
		// physical file or directory

		String realPath = sc.getRealPath(requestURI);

		if (realPath == null) return new WpMatch();

		int pos = realPath.indexOf("\\ofsocial\\ofsocial");

		if (pos > -1)
		{
			realPath = realPath.substring(0, pos) + "\\ofsocial" + realPath.substring(pos + 18);
		}

		int pos2 = realPath.indexOf("/ofsocial/ofsocial");

		if (pos2 > -1)
		{
			realPath = realPath.substring(0, pos2) + "/ofsocial" + realPath.substring(pos2 + 18);
		}

		File f = new File(realPath);

		Log.debug("matches file " + requestURI + " " + realPath + " " + f.isFile() + " " + f.isDirectory());

		if (f.isFile() || f.isDirectory() || f.isHidden()) return null;

		// Return the RewriteMatch object
		return new WpMatch();
	}
}
