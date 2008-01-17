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

package org.jivesoftware.admin;

import org.dom4j.Element;
import org.jivesoftware.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;
import java.util.List;

/**
 * <p>A simple JSP tag for displaying tab information in the admin console. The
 * {@link SidebarTag} is similiar to this one.</p>
 *
 * <p>Attributes: <ul>
 *      <li><tt>bean</tt> (required) - the id of the request attribute which is a
 *      {@link AdminPageBean} instance. This class holds information
 *      needed to properly render the admin console tabs.</li>
 *      <li><tt>css</tt> (optional) - the CSS class name used to decorate the LI of the tab.</li>
 *      <li><tt>currentcss</tt> (optional) - the CSS class name used to decorate the LI of the
 *      currently selected tab.</li></ul></p>
 *
 * <p>This class assumes there is a request attribute with the name specified by the bean attribute.</p>
 *
 * <p>This tag prints out minimal HTML. It basically prints an unordered list (UL element) with each
 * LI containing an "A" tag specfied by the body content of this tag. For example, the body should contain
 * a template A tag which will have its values replaced at runtime: <ul><tt>
 *
 *      &lt;jive:tabs bean="jivepageinfo"&gt; <br>
 *          &nbsp;&nbsp;&nbsp;&lt;a href="[url]" title="[description]"&gt;[name]&lt;/a&gt; <br>
 *      &lt;/jive:tabs&gt;</tt></ul>
 *
 * Available token are: <ul>
 *      <li><tt>[id]</tt> - the ID of the tab, usually not needed.</li>
 *      <li><tt>[name]</tt> - the name of the tab, should be thought of as the display name.</li>
 *      <li><tt>[url]</tt> - the URL of the tab.</li>
 *      <li><tt>[description]</tt> - the description of the tab, good for mouse rollovers.</li></ul></p>
 */
public class TabsTag extends BodyTagSupport {

    private String bean;
    private String css;
    private String currentcss;
    private Boolean justlinks = false;

    /**
     * The name of the request attribute which holds a {@link AdminPageBean} instance.
     */
    public String getBean() {
        return bean;
    }

    /**
     * Sets the name of the request attribute to hold a {@link AdminPageBean} instance.
     */
    public void setBean(String bean) {
        this.bean = bean;
    }

    /**
     * Returns the value of the CSS class to be used for tab decoration. If not set will return a blank string.
     */
    public String getCss() {
        return clean(css);
    }

    /**
     * Sets the CSS used for tab decoration.
     */
    public void setCss(String css) {
        this.css = css;
    }

    /**
     * Returns the value of the CSS class to be used for the currently selected LI (tab). If not set will
     * return a blank string.
     */
    public String getCurrentcss() {
        return clean(currentcss);
    }

    /**
     * Sets the CSS class value for the currently selected tab.
     */
    public void setCurrentcss(String currentcss) {
        this.currentcss = currentcss;
    }

    /**
     * Returns whether we are in just links mode.
     */
    public Boolean getJustlinks() {
        return justlinks;
    }

    /**
     * Sets whether we are just to display links, no list.
     */
    public void setJustlinks(Boolean justlinks) {
        this.justlinks = justlinks;
    }

    /**
     * Does nothing, returns {@link #EVAL_BODY_BUFFERED} always.
     */
    public int doStartTag() throws JspException {
        return EVAL_BODY_BUFFERED;
    }

    /**
     * Gets the {@link AdminPageBean} instance from the request. If it doesn't exist then execution is stopped
     * and nothing is printed. If it exists, retrieve values from it and render the tabs. The body content
     * of the tag is assumed to have an A tag in it with tokens to replace (see class description).
     *
     * @return {@link #EVAL_PAGE} after rendering the tabs.
     * @throws JspException if an exception occurs while rendering the tabs.
     */
    public int doEndTag() throws JspException {
        HttpServletRequest request = (HttpServletRequest)pageContext.getRequest();
        // Get the page data bean from the request:
        // If the page info bean is not in the request then no tab will be selected - so, it'll fail gracefully
        String pageID = (String)request.getAttribute("pageID");
        // Get tabs from the model:
        List tabs = AdminConsole.getModel().selectNodes("//tab");
        if (tabs.size() > 0) {
            JspWriter out = pageContext.getOut();
            // Build up the output in a buffer (is probably faster than a bunch of out.write's)
            StringBuilder buf = new StringBuilder();
            if (!justlinks) { buf.append("<ul>"); }
            String body = getBodyContent().getString();
            // For each tab, print out an <LI>.
            Element currentTab = null;
            if (pageID != null) {
                currentTab = (Element)AdminConsole.getModel().selectSingleNode(
                            "//*[@id='" + pageID + "']/ancestor::tab");
            }
            for (int i=0; i<tabs.size(); i++) {
                Element tab = (Element)tabs.get(i);
                String value = body;
                if (value != null) {
                    // The URL for the tab should be the URL of the first item in the tab.
                    String pluginName = tab.attributeValue("plugin");
                    value = StringUtils.replace(value, "[id]", clean(tab.attributeValue("id")));
                    value = StringUtils.replace(value, "[url]",
                            request.getContextPath() + "/" + clean(tab.attributeValue("url")));
                    value = StringUtils.replace(value, "[name]",
                            clean(AdminConsole.getAdminText(tab.attributeValue("name"), pluginName)));
                    value = StringUtils.replace(value, "[description]",
                            clean(AdminConsole.getAdminText(tab.attributeValue("description"), pluginName)));
                }
                String css = getCss();
                if (tab.equals(currentTab)) {
                    css = getCurrentcss();
                }
                if (!justlinks) { buf.append("<li class=\"").append(css).append("\">"); }
                if (justlinks && i > 0) { buf.append(" | "); }
                buf.append(value);
                if (!justlinks) { buf.append("</li>"); }
            }

            if (!justlinks) { buf.append("</ul>"); }
            try {
                out.write(buf.toString());
            }
            catch (IOException ioe) {
                throw new JspException(ioe.getMessage());
            }
        }
        return EVAL_PAGE;
    }

    /**
     * Cleans the given string - if it's null, it's converted to a blank string. If it has ' then those are
     * converted to double ' so HTML isn't screwed up.
     *
     * @param in the string to clean
     * @return a cleaned version - not null and with escaped characters.
     */
    private String clean(String in) {
        return (in == null ? "" : StringUtils.replace(in, "'", "\\'"));
    }
}