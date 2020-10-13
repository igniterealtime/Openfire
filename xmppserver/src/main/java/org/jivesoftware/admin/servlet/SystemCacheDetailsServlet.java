package org.jivesoftware.admin.servlet;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;
import org.jivesoftware.util.CookieUtils;
import org.jivesoftware.util.ListPager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.ParamUtils;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.WebManager;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@SuppressWarnings("serial")
@WebServlet(value = "/SystemCacheDetails.jsp")
public class SystemCacheDetailsServlet extends HttpServlet {

    private static final String[] SEARCH_FIELDS = {"cacheName", "searchKey", "searchValue"};

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {

        final String cacheName = ParamUtils.getStringParameter(request, "cacheName", "").trim();
        final Optional<Cache<?, ?>> optionalCache = Arrays.stream(CacheFactory.getAllCaches())
            .filter(cache -> cacheName.equals(cache.getName()))
            .findAny()
            .map(cache -> (Cache<?, ?>) cache);

        if (!optionalCache.isPresent()) {
            request.setAttribute("warningMessage", LocaleUtils.getLocalizedString("system.cache-details.cache_not_found", Collections.singletonList(StringUtils.escapeHTMLTags(cacheName))));
        }

        final boolean secretKey = optionalCache.map(Cache::isKeySecret).orElse(Boolean.FALSE);
        final boolean secretValue = optionalCache.map(Cache::isValueSecret).orElse(Boolean.FALSE);

        final List<Map.Entry<String, String>> cacheEntries = optionalCache.map(Cache::entrySet)
            .map(Collection::stream)
            .orElseGet(Stream::empty)
            .map(entry -> new AbstractMap.SimpleEntry<>(secretKey ? "************" : entry.getKey().toString(), secretValue ? "************" : entry.getValue().toString()))
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toList());

        // Find what we're searching for
        final Search search = new Search(request);
        Predicate<Map.Entry<String, String>> predicate = entry -> true;
        if (!search.key.isEmpty() && !secretKey) {
            predicate = predicate.and(entry -> StringUtils.containsIgnoringCase(entry.getKey(), search.key));
        }
        if (!search.value.isEmpty() && !secretValue) {
            predicate = predicate.and(entry -> StringUtils.containsIgnoringCase(entry.getValue(), search.value));
        }

        final ListPager<Map.Entry<String, String>> listPager = new ListPager<>(request, response, cacheEntries, predicate, SEARCH_FIELDS);

        final String csrf = StringUtils.randomString(16);
        CookieUtils.setCookie(request, response, "csrf", csrf, -1);
        addSessionFlashes(request, "errorMessage", "warningMessage", "successMessage");
        request.setAttribute("csrf", csrf);
        request.setAttribute("cacheName", cacheName);
        request.setAttribute("listPager", listPager);
        request.setAttribute("search", search);
        request.getRequestDispatcher("system-cache-details.jsp").forward(request, response);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final HttpSession session = request.getSession();
        final Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
        if (csrfCookie == null || !csrfCookie.getValue().equals(request.getParameter("csrf"))) {
            session.setAttribute("errorMessage", LocaleUtils.getLocalizedString("global.csrf.failed"));
        } else {
            final WebManager webManager = new WebManager();
            webManager.init(request, response, session, session.getServletContext());
            final String action = ParamUtils.getStringParameter(request, "action", "");
            switch (action) {
                case "delete":
                    deleteProperty(request, response, session);
                    break;
                case "cancel":
                    session.setAttribute("warningMessage", LocaleUtils.getLocalizedString("system.cache-details.cancelled"));
                    break;
                default:
                    session.setAttribute("warningMessage", LocaleUtils.getLocalizedString("global.request-error-no-such-action", Collections.singletonList(action)));
                    break;
            }
        }
        response.sendRedirect(request.getRequestURI() + ListPager.getQueryString(request, '?', SEARCH_FIELDS));
    }

    private void deleteProperty(final HttpServletRequest request, final HttpServletResponse response, final HttpSession session) {
        final String cacheName = ParamUtils.getStringParameter(request, "cacheName", "").trim();
        final String key = ParamUtils.getStringParameter(request, "key", "");
        final Optional<Cache<?, ?>> optionalCache = Arrays.stream(CacheFactory.getAllCaches())
            .filter(cache -> cacheName.equals(cache.getName()))
            .findAny()
            .map(cache -> (Cache<?, ?>) cache);
        if(optionalCache.isPresent()) {
            if(optionalCache.get().remove(key) != null ) {
                session.setAttribute("successMessage", LocaleUtils.getLocalizedString("system.cache-details.deleted",
                    Collections.singletonList(StringEscapeUtils.escapeXml11(key))));
                final WebManager webManager = new WebManager();
                webManager.init(request, response, session, session.getServletContext());
                webManager.logEvent(String.format("Key '%s' deleted from cache '%s'", key, cacheName), null);
            } else {
                session.setAttribute("errorMessage", LocaleUtils.getLocalizedString("system.cache-details.key_not_found",
                    Collections.singletonList(StringEscapeUtils.escapeXml11(key))));
            }
        } else {
            request.setAttribute("warningMessage", LocaleUtils.getLocalizedString("system.cache-details.cache_not_found",
                Collections.singletonList(StringEscapeUtils.escapeXml11(cacheName))));
        }
    }

    private static void addSessionFlashes(final HttpServletRequest request, final String... flashes) {
        final HttpSession session = request.getSession();
        for (final String flash : flashes) {
            final Object flashValue = session.getAttribute(flash);
            if (flashValue != null) {
                request.setAttribute(flash, flashValue);
                session.setAttribute(flash, null);
            }
        }
    }

    /**
     * Represents the entries being searched for
     */
    public static final class Search {

        private final String key;
        private final String value;

        public Search(final HttpServletRequest request) {
            this.key = ParamUtils.getStringParameter(request, "searchKey", "").trim();
            this.value = ParamUtils.getStringParameter(request, "searchValue", "").trim();
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

    }
}
