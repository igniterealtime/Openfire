/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import javax.servlet.*;
import java.io.IOException;


/**
 * Sets the character encoding to UTF-8.
 *
 * @author Matt Tucker
 */
public class SetCharacterEncodingFilter implements Filter {

    public void init(FilterConfig filterConfig) throws ServletException {

    }

    public void destroy() {
        
    }

    /**
     * Sets the character encoding to be used for any content passing out of this filter.
     */
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException
    {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=" + "UTF-8");
        chain.doFilter(request, response);
    }
}
