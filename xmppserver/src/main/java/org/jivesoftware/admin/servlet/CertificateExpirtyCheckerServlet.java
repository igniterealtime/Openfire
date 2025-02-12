/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.util.*;
import org.jivesoftware.util.cert.CertificateExpiryChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet for the admin console page that configures the automated, continuous checks of TLS certificate expiry.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@WebServlet(value = "/security-certificate-expiry-check.jsp")
public class CertificateExpirtyCheckerServlet extends HttpServlet
{
    protected void setStandardAttributes(final HttpServletRequest request, final HttpServletResponse response, final WebManager webManager)
    {
        final String csrf = StringUtils.randomString(16);
        CookieUtils.setCookie(request, response, "csrf", csrf, -1);
        request.setAttribute("csrf", csrf);

        request.setAttribute("isEnabled", CertificateExpiryChecker.ENABLED.getValue());
        request.setAttribute("isNotifyAdmins", CertificateExpiryChecker.NOTIFY_ADMINS.getValue());
        request.setAttribute("frequencyHours", CertificateExpiryChecker.FREQUENCY.getValue().toHours());
        request.setAttribute("warningPeriodHours", CertificateExpiryChecker.WARNING_PERIOD.getValue().toHours());
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
    {
        final HttpSession session = request.getSession();
        final WebManager webManager = new WebManager();
        webManager.init(request, response, session, session.getServletContext());

        setStandardAttributes(request, response, webManager);

        request.getRequestDispatcher("security-certificate-expiry-check-page.jsp").forward(request, response);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
    {
        final HttpSession session = request.getSession();
        final WebManager webManager = new WebManager();
        webManager.init(request, response, session, session.getServletContext());

        final Map<String, Object> errors = new HashMap<>();

        final Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
        if (csrfCookie == null || !csrfCookie.getValue().equals(request.getParameter("csrf"))) {
            errors.put("csrf", null);
        } else {
            final long frequencyHours = ParamUtils.getLongParameter(request, "frequencyHours", -1);
            final long warningPeriodHours = ParamUtils.getLongParameter(request, "warningPeriodHours", -1);

            // Checkboxes that are unchecked do not register as a parameter. Thus, if the parameter is absent, then the user unchecked the checkbox!
            final boolean isEnabled = ParamUtils.getBooleanParameter(request, "isEnabled", false);
            final boolean isNotifyAdmins = ParamUtils.getBooleanParameter(request, "isNotifyAdmins", false);

            if (frequencyHours <= 0) {
                errors.put("frequency", null);
            }
            final Duration frequency = Duration.ofHours(frequencyHours);

            if (warningPeriodHours <= 0) {
                errors.put("warningPeriod", null);
            }
            final Duration warningPeriod = Duration.ofHours(warningPeriodHours);

            setStandardAttributes(request, response, webManager);

            if (errors.isEmpty())
            {
                // Place back the user-provided values (even if they're incorrect) so that they can be modified.
                request.setAttribute("isEnabled", isEnabled);
                request.setAttribute("isNotifyAdmins", isNotifyAdmins);
                if (ParamUtils.getParameter(request, "frequencyHours") != null) request.setAttribute("frequencyHours", ParamUtils.getParameter(request, "frequencyHours"));
                if (ParamUtils.getParameter(request, "warningPeriodHours") != null) request.setAttribute("warningPeriodHours", ParamUtils.getParameter(request, "warningPeriodHours"));

                CertificateExpiryChecker.ENABLED.setValue(isEnabled);
                CertificateExpiryChecker.NOTIFY_ADMINS.setValue(isNotifyAdmins);
                CertificateExpiryChecker.FREQUENCY.setValue(frequency);
                CertificateExpiryChecker.WARNING_PERIOD.setValue(warningPeriod);

                webManager.logEvent("Edited TLS certificate expiry check settings", "enabled = "+isEnabled+", notificationsEnabled = "+isNotifyAdmins+", frequency = "+frequency+", warningPeriod = "+warningPeriod);
            }
        }

        request.setAttribute("errors", errors);
        request.getRequestDispatcher("security-certificate-expiry-check-page.jsp?success=" + (errors.isEmpty() ? "true" : "false")).forward(request, response);
    }
}
