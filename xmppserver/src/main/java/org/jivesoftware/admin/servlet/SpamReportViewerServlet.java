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

@WebServlet(value = "/spam-report-viewer.jsp")
public class SpamReportViewerServlet extends HttpServlet {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");

    private static final String[] SEARCH_FIELDS = {"searchReporter", "searchReported", "searchFrom", "searchTo"};

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final SpamReportManager spamReportManager = SpamReportManager.getInstance();
        final List<SpamReport> spamReports = spamReportManager.getSpamReports();

        final Search search = new Search(request);
        Predicate<SpamReport> predicate = spamReport -> true;
        if (!search.reporter.isEmpty()) {
            predicate = predicate.and(spamReport -> StringUtils.containsIgnoringCase(spamReport.getReportingAddress().toString(), search.reporter));
        }
        if (!search.reported.isEmpty()) {
            predicate = predicate.and(spamReport -> StringUtils.containsIgnoringCase(spamReport.getReportedAddress().toString(), search.reported));
        }

        Optional<Date> from;
        if (!search.from.isEmpty()) {
            from = parseSearchDate(search.from);
            if (!from.isPresent()) {
                // Nothing matches a bad date!
                predicate = spamReport -> false;
            }
        } else {
            from = Optional.empty();
        }

        Optional<Date> to;
        if (!search.to.isEmpty()) {
            to = parseSearchDate(search.to);
            if (!to.isPresent()) {
                // Nothing matches a bad date!
                predicate = spamReport -> false;
            }
        } else {
            to = Optional.empty();
        }

        // Make sure the from/to are the correct way around
        if (from.isPresent() && to.isPresent() && from.get().after(to.get())) {
            final Optional<Date> temp = to;
            to = from;
            from = temp;
        }

        if (from.isPresent()) {
            final Date date = from.get();
            predicate = predicate.and(spamReport -> !spamReport.getTimestamp().isBefore(date.toInstant()));
        }

        if (to.isPresent()) {
            // Intuitively the end date is exclusive, so add an extra day
            final Instant date = to.get().toInstant().plus(1, ChronoUnit.DAYS);
            predicate = predicate.and(spamReport -> spamReport.getTimestamp().isBefore(date));
        }

        final ListPager<SpamReport> listPager = new ListPager<>(request, response, spamReports, predicate, SEARCH_FIELDS);

        request.setAttribute("spamReportManager", spamReportManager);
        request.setAttribute("listPager", listPager);
        request.setAttribute("search", search);
        request.getRequestDispatcher("spam-report-viewer-jsp.jsp").forward(request, response);
    }

    private static Optional<Date> parseSearchDate(final String searchDate) {
        try {
            return Optional.ofNullable(DATE_FORMAT.parse(searchDate));
        } catch (ParseException e) {
            return Optional.empty();
        }
    }

    public static class Search {
        private final String reporter;
        private final String reported;
        private final String from;
        private final String to;

        public Search(final HttpServletRequest request) {
            this.reporter = ParamUtils.getStringParameter(request, "searchReporter", "").trim();
            this.reported = ParamUtils.getStringParameter(request, "searchReported", "").trim();
            this.from = ParamUtils.getStringParameter(request, "searchFrom", "").trim();
            this.to = ParamUtils.getStringParameter(request, "searchTo", "").trim();
        }

        public String getReporter() {
            return reporter;
        }

        public String getReported() {
            return reported;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }
    }
}
