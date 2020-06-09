package org.jivesoftware.admin.servlet;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.jivesoftware.util.CookieUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.ListPager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.ParamUtils;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.WebManager;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@SuppressWarnings("serial")
@WebServlet(value = "/server-properties.jsp")
public class SystemPropertiesServlet extends HttpServlet {

    private static final String[] SEARCH_FIELDS = {"searchName", "searchValue", "searchDefaultValue", "searchPlugin", "searchDescription", "searchDynamic"};

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

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {

        final Collection<SystemProperty> systemProperties = SystemProperty.getProperties();
        final Set<String> systemPropertyKeys = systemProperties.stream().map(SystemProperty::getKey).collect(Collectors.toSet());
        // Get all the SystemProperties
        final List<CompoundProperty> compoundProperties = systemProperties.stream().map(CompoundProperty::new).collect(Collectors.toList());
        // Now add any missing JiveGlobals properties
        JiveGlobals.getPropertyNames().stream().filter(key -> !systemPropertyKeys.contains(key)).forEach(key -> compoundProperties.add(new CompoundProperty(key)));
        // And sort by key
        compoundProperties.sort(Comparator.comparing(o -> o.key));

        // Find what we're searching for
        final Search search = new Search(request);

        final Predicate<CompoundProperty> predicate = getSearchPredicate(search);

        // Finally, create a list pager
        final ListPager<CompoundProperty> listPager = new ListPager<>(request, response, compoundProperties, predicate, SEARCH_FIELDS);

        final List<String> plugins = compoundProperties.stream()
            .map(compoundProperty -> compoundProperty.plugin)
            .distinct()
            .filter(plugin -> !plugin.isEmpty())
            // Ensure Openfire comes first in the list
            .sorted((o1, o2) -> {
                if (o1.equalsIgnoreCase("Openfire")) {
                    return -1;
                } else if (o2.equalsIgnoreCase("Openfire")) {
                    return 1;
                } else {
                    return o1.compareTo(o2);
                }
            })
            .collect(Collectors.toList());

        addSessionFlashes(request, "errorMessage", "warningMessage", "successMessage");
        final String csrf = StringUtils.randomString(16);
        CookieUtils.setCookie(request, response, "csrf", csrf, -1);
        request.setAttribute("csrf", csrf);
        request.setAttribute("listPager", listPager);
        request.setAttribute("search", search);
        request.setAttribute("plugins", plugins);

        request.getRequestDispatcher("system-properties.jsp").forward(request, response);
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
                case "save":
                    saveProperty(request, webManager);
                    break;
                case "cancel":
                    session.setAttribute("warningMessage",
                        String.format("No changes were made to the property %s",
                            StringEscapeUtils.escapeXml11(request.getParameter("key"))));
                    break;
                case "encrypt":
                    encryptProperty(request, webManager);
                    break;
                case "delete":
                    deleteProperty(request, webManager);
                    break;
                default:
                    session.setAttribute("warningMessage",
                        String.format("Unexpected request action '%s'",
                            StringEscapeUtils.escapeXml11(action)));
                    break;
            }
        }
        response.sendRedirect(request.getRequestURI() + ListPager.getQueryString(request, '?', SEARCH_FIELDS));
    }

    private void saveProperty(final HttpServletRequest request, final WebManager webManager) {
        final String key = request.getParameter("key");
        final boolean oldEncrypt = JiveGlobals.isPropertyEncrypted(key);
        final String oldValueToLog = oldEncrypt ? "***********" : JiveGlobals.getProperty(key);
        final String value = request.getParameter("value");
        final boolean encrypt = ParamUtils.getBooleanAttribute(request, "encrypt");
        final boolean alreadyExists = JiveGlobals.getProperty(key) != null;
        JiveGlobals.setProperty(key, value, encrypt);
        request.getSession().setAttribute("successMessage",
            String.format("The property %s was %s",
                StringEscapeUtils.escapeXml11(key), alreadyExists ? "updated" : "created"));
        final String newValueToLog = encrypt ? "***********" : value;
        final String details = alreadyExists
            ? String.format("Value of property changed from '%s' to '%s'", oldValueToLog, newValueToLog)
            : String.format("Property created with value '%s'", newValueToLog);
        webManager.logEvent("Updated server property " + key, details);
    }

    @SuppressWarnings("unchecked")
    private void encryptProperty(final HttpServletRequest request, final WebManager webManager) {
        final String key = request.getParameter("key");
        if (JiveGlobals.getProperty(key) == null) {
            // We can't encrypt a property that doesn't yet exist
            SystemProperty.getProperty(key).ifPresent(property -> property.setValue(property.getDefaultValue()));
        }
        JiveGlobals.setPropertyEncrypted(key, true);
        webManager.logEvent("Encrypted server property " + key, null);
        request.getSession().setAttribute("successMessage",
            String.format("The property %s was encrypted", StringEscapeUtils.escapeXml11(key)));
    }

    private void deleteProperty(final HttpServletRequest request, final WebManager webManager) {
        final String key = request.getParameter("key");
        JiveGlobals.deleteProperty(key);
        webManager.logEvent("deleted server property " + key, null);
        request.getSession().setAttribute("successMessage",
            String.format("The property %s was deleted", StringEscapeUtils.escapeXml11(key)));
    }

    private Predicate<CompoundProperty> getSearchPredicate(final Search search) {
        Predicate<CompoundProperty> predicate = compoundProperty -> true;

        if (!search.name.isEmpty()) {
            predicate = predicate.and(compoundProperty -> StringUtils.containsIgnoringCase(compoundProperty.key, search.name));
        }

        if (!search.value.isEmpty()) {
            predicate = predicate.and(compoundProperty -> !compoundProperty.isEncrypted() && (StringUtils.containsIgnoringCase(compoundProperty.valueAsSaved, search.value) || StringUtils.containsIgnoringCase(compoundProperty.displayValue, search.value)));
        }

        switch (search.defaultValue) {
            case "changed":
                predicate = predicate.and(compoundProperty -> !compoundProperty.hidden && compoundProperty.systemProperty && compoundProperty.valueChanged);
                break;
            case "unchanged":
                predicate = predicate.and(compoundProperty -> !compoundProperty.hidden && compoundProperty.systemProperty && !compoundProperty.valueChanged);
                break;
            case "unknown":
                predicate = predicate.and(compoundProperty -> compoundProperty.hidden || !compoundProperty.systemProperty);
                break;
            default:
                // Do nothing
        }

        switch (search.plugin) {
            case "":
                // All plugins, no filter required
                break;
            case "none":
                predicate = predicate.and(compoundProperty -> compoundProperty.plugin.isEmpty());
                break;
            default:
                predicate = predicate.and(compoundProperty -> search.plugin.equals(compoundProperty.plugin));
        }

        if (!search.description.isEmpty()) {
            predicate = predicate.and(compoundProperty -> StringUtils.containsIgnoringCase(compoundProperty.description, search.description));
        }

        switch (search.dynamic) {
            case "true":
                predicate = predicate.and(compoundProperty -> compoundProperty.dynamic);
                break;
            case "false":
                predicate = predicate.and(compoundProperty -> compoundProperty.systemProperty && !compoundProperty.dynamic && !compoundProperty.restartRequired);
                break;
            case "restart":
                predicate = predicate.and(compoundProperty -> compoundProperty.systemProperty && compoundProperty.restartRequired);
                break;
            case "unknown":
                predicate = predicate.and(compoundProperty -> !compoundProperty.systemProperty);
            default:
                // Do nothing
        }

        return predicate;
    }

    /**
     * Not every entry in the ofProperty table will have a matching {@link SystemProperty} - so this class exists so
     * that the admin UI can display either
     */
    public static final class CompoundProperty {

        private final boolean systemProperty;
        private final String key;
        private final Object value;
        private final String valueAsSaved;
        private final String displayValue;
        private final String defaultDisplayValue;
        private final boolean valueChanged;
        private final String description;
        private final String plugin;
        private final boolean dynamic;
        private final boolean restartRequired;
        private final boolean encrypted;
        private final boolean hidden;

        private CompoundProperty(final String key) {
            this.systemProperty = false;
            this.key = key;
            this.value = JiveGlobals.getProperty(key);
            this.valueAsSaved = (String) value;
            this.displayValue = valueAsSaved;
            this.defaultDisplayValue = null;
            this.valueChanged = false;
            this.description = "";
            this.plugin = "";
            this.dynamic = false;
            this.restartRequired = false;
            this.encrypted = JiveGlobals.isPropertyEncrypted(key);
            this.hidden = encrypted || JiveGlobals.isPropertySensitive(key);
        }

        private CompoundProperty(final SystemProperty<?> systemProperty) {
            this.systemProperty = true;
            this.key = systemProperty.getKey();
            this.value = systemProperty.getValue();
            this.valueAsSaved = systemProperty.getValueAsSaved();
            this.displayValue = systemProperty.getDisplayValue();
            this.defaultDisplayValue = systemProperty.getDefaultDisplayValue();
            this.valueChanged = systemProperty.hasValueChanged();
            this.description = systemProperty.getDescription();
            this.plugin = systemProperty.getPlugin();
            this.dynamic = systemProperty.isDynamic();
            this.restartRequired = systemProperty.isRestartRequired();
            this.encrypted = systemProperty.isEncrypted();
            this.hidden = encrypted || JiveGlobals.isPropertySensitive(key);
        }

        public String getKey() {
            return key;
        }

        public String getValueAsSaved() {
            return valueAsSaved;
        }

        public String getDisplayValue() {
            return displayValue;
        }

        public String getDefaultDisplayValue() {
            return defaultDisplayValue;
        }

        public boolean isSystemProperty() {
            return systemProperty;
        }

        public String getPlugin() {
            return plugin;
        }

        public String getDescription() {
            return description;
        }

        public boolean isDynamic() {
            return dynamic;
        }

        public boolean isRestartRequired() {
            return restartRequired;
        }

        public boolean isHidden() {
            return hidden;
        }

        public boolean isEncrypted() {
            return encrypted;
        }
    }

    /**
     * Represents the properties being searched for
     */
    public static final class Search {

        private final String name;
        private final String value;
        private final String defaultValue;
        private final String plugin;
        private final String description;
        private final String dynamic;

        public Search(final HttpServletRequest request) {
            this.name = ParamUtils.getStringParameter(request, "searchName", "").trim();
            this.value = ParamUtils.getStringParameter(request, "searchValue", "").trim();
            this.defaultValue = ParamUtils.getStringParameter(request, "searchDefaultValue", "").trim();
            this.plugin = ParamUtils.getStringParameter(request, "searchPlugin", "").trim();
            this.description = ParamUtils.getStringParameter(request, "searchDescription", "").trim();
            this.dynamic = ParamUtils.getStringParameter(request, "searchDynamic", "").trim();
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getPlugin() {
            return plugin;
        }

        public String getDescription() {
            return description;
        }

        public String getDynamic() {
            return dynamic;
        }

    }
}
