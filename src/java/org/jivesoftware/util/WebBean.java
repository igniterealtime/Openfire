/*
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

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;

public abstract class WebBean {

    public HttpSession session;
    public HttpServletRequest request;
    public HttpServletResponse response;
    public ServletContext application;
    public JspWriter out;

    public void init(HttpServletRequest request, HttpServletResponse response,
            HttpSession session, ServletContext app, JspWriter out)
    {
        this.request = request;
        this.response = response;
        this.session = session;
        this.application = app;
        this.out = out;
    }

    public void init(HttpServletRequest request, HttpServletResponse response,
                     HttpSession session, ServletContext app) {

        this.request = request;
        this.response = response;
        this.session = session;
        this.application = app;
    }

    public void init(PageContext pageContext){
        this.request = (HttpServletRequest)pageContext.getRequest();
        this.response = (HttpServletResponse)pageContext.getResponse();
        this.session = pageContext.getSession();
        this.application = pageContext.getServletContext();
        this.out = pageContext.getOut();
    }
}
