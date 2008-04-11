/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.jstl.core.Config;
import javax.servlet.jsp.jstl.fmt.LocalizationContext;
import java.io.IOException;
import java.util.ResourceBundle;

/**
 * Sets the locale context-wide.
 */
public class LocaleFilter implements Filter {

    private ServletContext context;

    public void init(FilterConfig config) throws ServletException {
        this.context = config.getServletContext();
    }

    /**
     * Ssets the locale context-wide based on a call to {@link JiveGlobals#getLocale()}.
     */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        final String pathInfo = ((HttpServletRequest)request).getPathInfo();

        if (pathInfo == null) {
            // Note, putting the locale in the application at this point is a little overkill
            // (ie, every user who hits this filter will do this). Eventually, it might make
            // sense to just set the locale in the user's session and if that's done we might
            // want to honor a preference to get the user's locale based on request headers.
            // For now, this is just a convenient place to set the locale globally.
            Config.set(context, Config.FMT_LOCALE, JiveGlobals.getLocale());
        }
        else {
            try {
                String[] parts = pathInfo.split("/");
                String pluginName = parts[1];
                ResourceBundle bundle = LocaleUtils.getPluginResourceBundle(pluginName);
                LocalizationContext ctx = new LocalizationContext(bundle, JiveGlobals.getLocale());
                Config.set(request, Config.FMT_LOCALIZATION_CONTEXT, ctx);
            }
            catch (Exception e) {
                // Note, putting the locale in the application at this point is a little overkill
                // (ie, every user who hits this filter will do this). Eventually, it might make
                // sense to just set the locale in the user's session and if that's done we might
                // want to honor a preference to get the user's locale based on request headers.
                // For now, this is just a convenient place to set the locale globally.
                Config.set(context, Config.FMT_LOCALE, JiveGlobals.getLocale());
            }
        }
        // Move along:
        chain.doFilter(request, response);
    }

    /**
     * Does nothing
     */
    public void destroy() {
    }
}
