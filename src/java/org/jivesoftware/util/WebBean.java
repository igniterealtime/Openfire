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

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspWriter;

public abstract class WebBean {

    public HttpSession session;
    public HttpServletRequest request;
    public HttpServletResponse response;
    public ServletContext application;
    public JspWriter out;

    public void init(HttpServletRequest request, HttpServletResponse response,
                     HttpSession session, ServletContext app, JspWriter out) {

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
}