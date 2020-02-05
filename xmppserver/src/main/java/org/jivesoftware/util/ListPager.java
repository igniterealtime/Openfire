package org.jivesoftware.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.text.StringEscapeUtils;

/**
 * <p>
 * This class provides an easy way to page through a filterable list of items from a JSP page.
 * </p>
 *
 * <p>
 * It is up to the caller of the class to specify the filter, if required, in the form of a
 * {@link java.util.function.Predicate predicate}. To ensure that search parameters are maintained when paging through
 * the list, it's also necessary to supply a list of input field identifiers that should be included as part of the
 * request when accessing another page. These field identifiers are used to extract the value of the field, and copy it
 * to a hidden form that is used for the actual submission. This means each search criteria has two form fields
 * associated with it - the visible field that the users types in, and in the hidden form that is submitted.
 * </p>
 *
 * <p>
 * The reason for this is that the location of the input fields on the screen may not always be appropriate for a
 * form - for example, if the fields are location within a table, the form must wrap the whole table, which would
 * preclude the use of any other forms within cells in that table.
 * </p>
 *
 * <p>
 * These input fields will also be modified so that pressing Enter/Return when the fields has focus will automatically
 * submit the form.
 * </p>
 *
 * @param <T> The type of item being displayed
 */
public class ListPager<T> {

    private static final String REQUEST_PARAMETER_KEY_PAGE_SIZE = "listPagerPageSize";
    private static final String REQUEST_PARAMETER_KEY_CURRENT_PAGE = "listPagerCurrentPage";
    private static final String PAGINATION_FORM_ID = "paginationForm";
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int[] PAGE_SIZES = {25, 50, 100, 1000};


    private final int totalItemCount;
    private final List<T> itemsOnPage;
    private final int filteredItemCount;
    private final int pageSize;
    private final int totalPages;
    private final int currentPage;
    private final int firstItemOnPage;
    private final int lastItemOnPage;
    private final String[] additionalFormFields;
    private final HttpServletRequest request;

    /**
     * Creates a unfiltered list pager.
     *
     * @param request              the request object for the page in question
     * @param response             the response object for the page in question
     * @param items                the list of items to display on the page
     * @param additionalFormFields 0 or more input field identifiers (<strong>NOT form field names</strong>) to include in requests for other pages
     */
    public ListPager(final HttpServletRequest request, final HttpServletResponse response, final List<T> items, final String... additionalFormFields) {
        this(request, response, items, item -> true, additionalFormFields);
    }

    /**
     * Creates a unfiltered list pager.
     *
     * @param request              the request object for the page in question
     * @param response             the response object for the page in question
     * @param items                the complete list of items to display on the page
     * @param filter               the filter to apply to the complete list
     * @param additionalFormFields 0 or more input field identifiers (<strong>NOT form field names</strong>) to include in requests for other pages
     */
    public ListPager(final HttpServletRequest request, final HttpServletResponse response, final List<T> items, final Predicate<T> filter, final String... additionalFormFields) {
        final HttpSession session = request.getSession();
        final WebManager webManager = new WebManager();
        webManager.init(request, response, session, session.getServletContext());
        final String requestURI = request.getRequestURI();
        final int initialPageSize = webManager.getRowsPerPage(requestURI, DEFAULT_PAGE_SIZE);
        final List<T> filteredItems = items.stream().filter(filter).collect(Collectors.toList());

        this.request = request;
        this.totalItemCount = items.size();
        this.filteredItemCount = filteredItems.size();
        this.pageSize = bound(ParamUtils.getIntParameter(request, REQUEST_PARAMETER_KEY_PAGE_SIZE, initialPageSize), PAGE_SIZES[PAGE_SIZES.length - 1]);
        // Even with no filtered items, we want to display at least one page
        this.totalPages = Math.max(1, (int) Math.ceil((double)filteredItemCount / (double)pageSize));
        // Bound the current page between 1 and the total number of pages
        this.currentPage = bound(ParamUtils.getIntParameter(request, REQUEST_PARAMETER_KEY_CURRENT_PAGE, 1), totalPages);
        this.firstItemOnPage = filteredItemCount == 0 ? 0 : (currentPage - 1) * pageSize + 1;
        this.lastItemOnPage = filteredItemCount == 0 ? 0 : Math.min(currentPage * pageSize, filteredItemCount);
        this.itemsOnPage = filteredItemCount == 0 ? Collections.emptyList() : filteredItems.subList(firstItemOnPage - 1, lastItemOnPage);
        this.additionalFormFields = additionalFormFields;

        webManager.setRowsPerPage(requestURI, pageSize);
    }

    private static int bound(final int value, final int maxValue) {
        return Math.min(maxValue, Math.max(1, value));
    }

    /**
     * @return the total number unfiltered items
     */
    public int getTotalItemCount() {
        return totalItemCount;
    }

    /**
     * @return the number of items after the filter has been applied
     */
    public int getFilteredItemCount() {
        return filteredItemCount;
    }

    /**
     * @return the filtered items on the current page
     */
    public List<T> getItemsOnCurrentPage() {
        return itemsOnPage;
    }

    /**
     * @return the current page
     */
    @SuppressWarnings("WeakerAccess")
    public int getCurrentPageNumber() {
        return currentPage;
    }

    /**
     * @return the maximum number of items on the page
     */
    @SuppressWarnings("unused")
    public int getPageSize() {
        return pageSize;
    }

    /**
     * @return the first item number on the page
     */
    public int getFirstItemNumberOnPage() {
        return firstItemOnPage;
    }

    /**
     * @return the last item number on the page
     */
    public int getLastItemNumberOnPage() {
        return lastItemOnPage;
    }

    /**
     * @return the total number of pages
     */
    public int getTotalPages() {
        return totalPages;
    }

    /**
     * @return {@code true} if the the supplied filter has restricted the number of items to display, otherwise {@code false}
     */
    public boolean isFiltered() {
        return totalItemCount != filteredItemCount;
    }

    /**
     * @return a string that contains HTML for selecting the page size
     */
    public String getPageSizeSelection() {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("<select name='%s' onchange='return setPageSize(this.value);'>", REQUEST_PARAMETER_KEY_PAGE_SIZE));
        for (final int optionSize : PAGE_SIZES) {
            sb.append(String.format("<option value='%d'%s>%d</option>", optionSize, pageSize == optionSize ? " selected" : "", optionSize));
        }
        sb.append("</select>");
        return sb.toString();
    }

    /**
     * @return a string that contains links to other pages in the list
     */
    public String getPageLinks() {
        final StringBuilder sb = new StringBuilder();
        final int beginningEndPage = Math.min(3, totalPages);
        appendLinkToPages(sb, 1, beginningEndPage);

        final int middleStartPage = Math.max(currentPage - 2, beginningEndPage + 1);
        final int middleEndPage = Math.min(currentPage + 2, totalPages);

        if (middleStartPage > (beginningEndPage + 1)) {
            sb.append("\n<span> ... </span>");
        }
        appendLinkToPages(sb, middleStartPage, middleEndPage);

        final int endStartPage = Math.max(totalPages - 2, middleEndPage + 1);
        if (endStartPage > (middleEndPage + 1)) {
            sb.append("\n<span> ... </span>");
        }

        appendLinkToPages(sb, endStartPage, totalPages);

        return sb.toString();
    }

    private void appendLinkToPages(final StringBuilder sb, final int firstPage, final int lastPage) {
        for (int i = firstPage; i <= lastPage; i++) {
            appendPageLink(sb, i);
        }
    }

    private void appendPageLink(final StringBuilder sb, final int pageToLink) {
        final String cssClass;
        if (currentPage == pageToLink) {
            cssClass = " class='jive-current'";
        } else {
            cssClass = "";
        }

        sb.append(String.format("\n<a href='%s' onclick='return jumpToPage(%d)'%s>%s</a>",
            String.format("?%s=%d", REQUEST_PARAMETER_KEY_CURRENT_PAGE, pageToLink),
            pageToLink,
            cssClass,
            pageToLink));
    }

    /**
     * @return the list of hidden fields required to maintain the current list pager state
     */
    public String getHiddenFields() {
        final StringBuilder sb = new StringBuilder("\n")
            .append(String.format("\t<input type='hidden' name='%s' value='%d'>\n", REQUEST_PARAMETER_KEY_PAGE_SIZE, pageSize))
            .append(String.format("\t<input type='hidden' name='%s' value='%d'>\n", REQUEST_PARAMETER_KEY_CURRENT_PAGE, currentPage));
        for (final String additionalFormField : additionalFormFields) {
            final String formFieldValue = ParamUtils.getStringParameter(request, additionalFormField, "");
            sb.append(String.format("\t<input type='hidden' name='%s' value='%s'%s>\n",
                additionalFormField,
                StringEscapeUtils.escapeXml11(formFieldValue),
                formFieldValue.isEmpty() ? " disabled" : ""));
        }
        return sb.toString();
    }

    /**
     * @param request The request to retrieve the values from
     * @param prefix The first char of the query string - either `?` or `&amp;`
     * @param additionalFormFields 0 or more form field names to include in requests for other pages
     * @return a query string required to maintain the current list pager state
     */
    public static String getQueryString(final HttpServletRequest request, final char prefix, final String... additionalFormFields) {
        final StringBuilder sb = new StringBuilder();
        char conjunction = prefix;
        final int currentPage = ParamUtils.getIntParameter(request, REQUEST_PARAMETER_KEY_CURRENT_PAGE, 1);
        if (currentPage > 1) {
            sb.append(conjunction)
                .append(String.format("%s=%d", REQUEST_PARAMETER_KEY_CURRENT_PAGE, currentPage));
            conjunction = '&';
        }
        final int pageSize = bound(ParamUtils.getIntParameter(request, REQUEST_PARAMETER_KEY_PAGE_SIZE, DEFAULT_PAGE_SIZE), PAGE_SIZES[PAGE_SIZES.length - 1]);
        if (pageSize != DEFAULT_PAGE_SIZE) {
            sb.append(conjunction)
                .append(String.format("%s=%d", REQUEST_PARAMETER_KEY_PAGE_SIZE, pageSize));
            conjunction = '&';
        }
        for (final String additionalFormField : additionalFormFields) {
            final String formFieldValue = ParamUtils.getStringParameter(request, additionalFormField, "").trim();
            if(!formFieldValue.isEmpty()) {
                String encodedValue;
                try {
                    encodedValue = URLEncoder.encode(formFieldValue, StandardCharsets.UTF_8.name());
                } catch (final UnsupportedEncodingException e) {
                   encodedValue = formFieldValue;
                }
                sb.append(conjunction)
                    .append(String.format("%s=%s", additionalFormField, encodedValue));
                conjunction = '&';
            }
        }
        return sb.toString();
    }

    /**
     * @return a string with an HTML form containing hidden fields, used for navigating between pages
     */
    public String getJumpToPageForm() {
        return "\n" +
            String.format("<form id='%s'>\n", PAGINATION_FORM_ID) +
            getHiddenFields() +
            "</form>\n";
    }

    /**
     * @return a string containing JavaScript that helps with navigation between pages
     */
    public String getPageFunctions() {
        final StringBuilder sb = new StringBuilder("\n")
            .append("\tvar additionalFormFields = [");
        // The list of additional form fields
        for (final String additionalFormField : additionalFormFields) {
            if (!additionalFormField.equals(additionalFormFields[0])) {
                sb.append(", ");
            }
            sb.append(String.format("'%s'", additionalFormField));
        }
        sb.append("];\n\n");

        sb.append("\tfunction jumpToPage(pageNumber) {\n")
            // Changes the current page number, and submits the form
            .append(String.format("\t\tvar formObject = document.getElementById('%s');\n", PAGINATION_FORM_ID))
            .append(String.format("\t\tformObject.%s.value = pageNumber;\n", REQUEST_PARAMETER_KEY_CURRENT_PAGE))
            .append("\t\tsubmitForm();\n")
            .append("\t\treturn false;\n")
            .append("\t}\n")
            .append("\n");

        sb.append("\tfunction setPageSize(pageSize) {\n")
            // Changes the current page size, and submits the form
            .append(String.format("\t\tvar formObject = document.getElementById('%s');\n", PAGINATION_FORM_ID))
            .append(String.format("\t\tformObject.%s.value = pageSize;\n", REQUEST_PARAMETER_KEY_PAGE_SIZE))
            .append("\t\tsubmitForm();\n")
            .append("\t\treturn false;\n")
            .append("\t}\n")
            .append("\n");

        sb.append("\tfunction submitForm() {\n")
            // Extracts any of the additional form field values, and adds them to the form before submitting it
            .append(String.format("\t\tvar formObject = document.getElementById('%s');\n", PAGINATION_FORM_ID))
            .append(String.format("\t\tfor(var i = 0; i < %d; i++) {\n", additionalFormFields.length))
            .append("\t\t\tvar field = document.getElementById(additionalFormFields[i]);\n")
            .append("\t\t\tif (field !== null) {\n")
            .append("\t\t\t\tvar formField = formObject[additionalFormFields[i]];\n")
            .append("\t\t\t\tif (typeof field !== 'object' || field.value === '') {\n")
            .append("\t\t\t\t\tformField.disabled = true;\n")
            .append("\t\t\t\t} else {\n")
            .append("\t\t\t\t\tformField.disabled = false;\n")
            .append("\t\t\t\t\tformField.value = field.value;\n")
            .append("\t\t\t\t}\n")
            .append("\t\t\t}\n")
            .append("\t\t}\n")
            .append("\t\t\n")
            .append("\t\tif (formObject['").append(REQUEST_PARAMETER_KEY_PAGE_SIZE).append("'].value === '").append(pageSize).append("') formObject['").append(REQUEST_PARAMETER_KEY_PAGE_SIZE).append("'].disabled=true;\n")
            .append("\t\tif (formObject['").append(REQUEST_PARAMETER_KEY_CURRENT_PAGE).append("'].value === '1') formObject['").append(REQUEST_PARAMETER_KEY_CURRENT_PAGE).append("'].disabled=true;\n")
            .append("\t\t\n")
            .append("\t\tformObject.submit();\n")
            .append("\t\treturn false;\n")
            .append("\t}\n")
            .append("\n");

        // Add mechanisms to auto-submit the form when hitting enter (two mechanisms for different browsers)
        sb.append("\tfunction inputFieldOnKeyDownEventListener(e) {\n")
            .append("\t\tif (e.keyCode === 13) {\n")
            .append("\t\t\tsubmitForm();\n")
            .append("\t\t\treturn false;\n")
            .append("\t\t}\n")
            .append("\t}\n")
            .append("\n");

        sb.append("\tfunction inputFieldOnInputEventListener() {\n")
            .append("\t\tif (this.value === '') {\n")
            .append("\t\t\tsubmitFilterForm();\n")
            .append("\t\t\treturn false;\n")
            .append("\t\t}\n")
            .append("\t};\n")
            .append("\n");

        // Finally, bind the functions to the fields
        sb.append(String.format("\tfor(var i = 0; i < %d; i++) {\n", additionalFormFields.length))
            .append("\t\tvar field = document.getElementById(additionalFormFields[i]);\n")
            .append("\t\tif (field !== null && typeof field === 'object') {\n")
            .append("\t\t\tfield.onkeydown = inputFieldOnKeyDownEventListener;\n")
            .append("\t\t\tfield.addEventListener('input', inputFieldOnInputEventListener);\n")
            .append("\t\t}\n")
            .append("\t}\n");

        return sb.toString();
    }

}
