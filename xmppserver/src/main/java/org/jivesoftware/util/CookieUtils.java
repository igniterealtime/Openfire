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

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CookieUtils {

    /**
     * Returns the specified cookie, or {@code null} if the cookie
     * does not exist. Note: because of the way that cookies are implemented
     * it's possible for multiple cookies with the same name to exist (but with
     * different domain values). This method will return the first cookie that
     * has a name match.
     *
     * @param request the servlet request.
     * @param name the name of the cookie.
     * @return the Cookie object if it exists, otherwise {@code null}.
     */
    public static Cookie getCookie(HttpServletRequest request, String name) {
        Cookie cookies[] = request.getCookies();
        // Return null if there are no cookies or the name is invalid.
        if (cookies == null || name == null || name.length() == 0) {
            return null;
        }
        // Otherwise, we  do a linear scan for the cookie.
        Cookie cookie = null;
        for (int i = 0; i < cookies.length; i++) {
            // If the current cookie name matches the one we're looking for, we've
            // found a matching cookie.
            if (cookies[i].getName().equals(name)) {
                cookie = cookies[i];
                // The best matching cookie will be the one that has the correct
                // domain name. If we've found the cookie with the correct domain name,
                // return it. Otherwise, we'll keep looking for a better match.
                if (request.getServerName().equals(cookie.getDomain())) {
                    break;
                }
            }
        }
        return cookie;
    }

    /**
     * Deletes the specified cookie.
     *
     * @param request the servlet request.
     * @param response the servlet response.
     * @param cookie the cookie object to be deleted.
     */
    public static void deleteCookie(HttpServletRequest request, HttpServletResponse response,
            Cookie cookie)
    {
        if (cookie != null) {
            // Invalidate the cookie
            String path = request.getContextPath() == null ? "/" : request.getContextPath();
            if ("".equals(path)) {
                path = "/";
            }
            cookie.setPath(path);
            cookie.setValue("");
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }
    }

    /**
     * Stores a value in a cookie. By default this cookie will persist for 30 days.
     *
     * @see #setCookie(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse,String,String,int)
     * @param request the servlet request.
     * @param response the servlet response.
     * @param name a name to identify the cookie.
     * @param value the value to store in the cookie.
     */
    public static void setCookie(HttpServletRequest request, HttpServletResponse response,
            String name, String value)
    {
        // Save the cookie value for 1 month
        setCookie(request, response, name, value, 60*60*24*30);
    }

    /**
     * Stores a value in a cookie. This cookie will persist for the amount
     * specified in the {@code saveTime} parameter.
     *
     * @see #setCookie(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse,String,String)
     * @param request the servlet request.
     * @param response the servlet response.
     * @param name a name to identify the cookie.
     * @param value the value to store in the cookie.
     * @param maxAge the time (in seconds) this cookie should live.
     */
    public static void setCookie(HttpServletRequest request, HttpServletResponse response,
            String name, String value, int maxAge)
    {
        // Check to make sure the new value is not null (appservers like Tomcat
        // 4 blow up if the value is null).
        if (value == null) {
            value = "";
        }
        String path = request.getContextPath() == null ? "/" : request.getContextPath();
        if ("".equals(path)) {
            path = "/";
        }
        Cookie cookie = new Cookie(name, value);
        cookie.setMaxAge(maxAge);
        cookie.setPath(path);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }
}
