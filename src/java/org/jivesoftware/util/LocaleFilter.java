/*
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

    @Override
    public void init(FilterConfig config) throws ServletException {
        this.context = config.getServletContext();
    }

    /**
     * Ssets the locale context-wide based on a call to {@link JiveGlobals#getLocale()}.
     */
    @Override
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
    @Override
    public void destroy() {
    }
}
