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

import org.jivesoftware.openfire.spamreporting.SpamReport;
import org.jivesoftware.openfire.spamreporting.SpamReportManager;
import org.jivesoftware.util.ListPager;
import org.jivesoftware.util.ParamUtils;
import org.jivesoftware.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@WebServlet(value = "/spam-report-details.jsp")
public class SpamReportDetailsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final long reportId = ParamUtils.getLongParameter(request, "reportId", -1L);
        final SpamReportManager spamReportManager = SpamReportManager.getInstance();
        final SpamReport spamReport = spamReportManager.getSpamReport(reportId);

        request.setAttribute("spamReport", spamReport);

        request.getRequestDispatcher("spam-report-details-jsp.jsp").forward(request, response);
    }
}
