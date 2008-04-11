/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.admin;

import org.jivesoftware.util.StringUtils;
import org.dom4j.Element;

import javax.servlet.jsp.tagext.BodyTagSupport;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Iterator;
import java.io.IOException;

/**
 * <p>A simple JSP tag for displaying sidebar information in the admin console. The
 * {@link TabsTag} is similiar to this one.</p>
 *
 * <p>Attributes: <ul>
 *      <li><tt>bean</tt> (required) - the id of the request attribute which is a
 *      {@link AdminPageBean} instance. This class holds information
 *      needed to properly render the admin console sidebar.</li>
 *      <li><tt>css</tt> (optional) - the CSS class name used to decorate the LI of the sidebar items.</li>
 *      <li><tt>currentcss</tt> (optional) - the CSS class name used to decorate the LI of the
 *      currently selected sidebar item.</li>
 *      <li><tt>heaadercss</tt> (optional) - the CSS class name used to decorate the LI of the header
 *      section.</li></ul></p>
 *
 * <p>This class assumes there is a request attribute with the name specified by the bean attribute.</p>
 *
 * <p>This tag prints out minimal HTML. It basically prints an unordered list (UL element) with each
 * LI containing an "A" tag specfied by the body content of this tag. For example, the body should contain
 * a template A tag which will have its values replaced at runtime: <ul><tt>
 *
 *      &lt;jive:sidebar bean="jivepageinfo"&gt; <br>
 *          &nbsp;&nbsp;&nbsp;&lt;a href="[url]" title="[description]"&gt;[name]&lt;/a&gt; <br>
 *          &nbsp;&nbsp;&nbsp;&lt;jive:subsidebar&gt; ... &lt;/jive:subsidebar&gt; <br>
 *      &lt;/jive:sidebar&gt;</tt></ul>
 *
 * There is a subsidebar tag for rendering the sub-sidebars. For more info, see the
 * {@link SubSidebarTag} class.</p>
 *
 * <p>Available tokens for the "A" tag are: <ul>
 *      <li><tt>[id]</tt> - the ID of the sidebar item, usually not needed.</li>
 *      <li><tt>[name]</tt> - the name of the sidebar item, should be thought of as the display name.</li>
 *      <li><tt>[url]</tt> - the URL of the sidebar item.</li>
 *      <li><tt>[description]</tt> - the description of the sidebar item, good for mouse rollovers.</li></ul></p>
 */
public class SidebarTag extends BodyTagSupport {

    private String css;
    private String currentcss;
    private String headercss;
    private SubSidebarTag subsidebarTag;

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
     * Returns the value of the CSS class to be used for sidebar header sections.
     */
    public String getHeadercss() {
        return headercss;
    }

    /**
     * Sets the CSS value used for the sidebar header sections.
     */
    public void setHeadercss(String headercss) {
        this.headercss = headercss;
    }

    /**
     * Returns the subsidebar tag - should be declared in the body of this tag (see class description).
     */
    public SubSidebarTag getSubsidebarTag() {
        return subsidebarTag;
    }

    /**
     * Sets the subsidebar tag - used by the container.
     */
    public void setSubSidebar(SubSidebarTag subsidebarTag) {
        this.subsidebarTag = subsidebarTag;
    }

    /**
     * Does nothing, returns {@link #EVAL_BODY_BUFFERED} always.
     */
    public int doStartTag() throws JspException {
        return EVAL_BODY_BUFFERED;
    }

    /**
     * Gets the {@link AdminPageBean} instance from the request. If it doesn't exist then execution is stopped
     * and nothing is printed. If it exists, retrieve values from it and render the sidebar items. The body content
     * of the tag is assumed to have an A tag in it with tokens to replace (see class description) as well
     * as having a subsidebar tag..
     *
     * @return {@link #EVAL_PAGE} after rendering the tabs.
     * @throws JspException if an exception occurs while rendering the sidebar items.
     */
    public int doEndTag() throws JspException {
        // Start by getting the request from the page context
        HttpServletRequest request = (HttpServletRequest)pageContext.getRequest();

        // Check for body of this tag and the child tag
        if (getBodyContent().getString() == null) {
            throw new JspException("Error, no template (body value) set for the sidebar tag.");
        }
        if (subsidebarTag.getBody() == null) {
            throw new JspException("Error, no template (body value) set for the subsidebar tag");
        }

        // Get the initial subpage and page IDs
        String subPageID = (String)request.getAttribute("subPageID");
        String pageID = (String)request.getAttribute("pageID");
        String subnavID = (String)request.getAttribute("subnavID");

        // If the pageID is null, use the subPageID to set it. If both the pageID and
        // subPageIDs are null, return because these are key to execution of the tag.
        if (subPageID != null || pageID != null) {

            if (pageID == null) {
                Element subPage = AdminConsole.getElemnetByID(subPageID);
                pageID = subPage.getParent().getParent().attributeValue("id");
            }

            // Top level menu items
            if (AdminConsole.getModel().elements().size() > 0) {
                JspWriter out = pageContext.getOut();
                StringBuilder buf = new StringBuilder();

                Element current = null;
                Element subcurrent = null;
                Element subnav = null;
                if (subPageID != null) {
                    subcurrent = AdminConsole.getElemnetByID(subPageID);
                }
                current = AdminConsole.getElemnetByID(pageID);
                if (current != null) {
                    subnav = current.getParent();
                }

                Element currentTab = (Element)AdminConsole.getModel().selectSingleNode(
                        "//*[@id='" + pageID + "']/ancestor::tab");

                boolean isSubmenu = false;
                if (subcurrent != null) {
                    isSubmenu = subcurrent.getParent().getParent().getName().equals("item");
                }

                // Loop through all items in the root, print them out
                if (currentTab != null && subnav != null) {
                    Element sidebar = subnav.getParent().getParent();
                    String header = sidebar.attributeValue("name");
                    String pluginName = sidebar.attributeValue("plugin");
                    // Print the header:
                    String hcss = getHeadercss();
                    if (hcss == null) {
                        hcss = "";
                    }
                    Collection items = subnav.elements();
                    if (items.size() > 0) {
                        buf.append("<ul>");
                        // Now print all items:
                        for (Object itemObj : items) {
                            Element item = (Element)itemObj;
                            String subitemID = item.attributeValue("id");
                            String subitemName = item.attributeValue("name");
                            String subitemURL = item.attributeValue("url");
                            String subitemDescr = item.attributeValue("description");
                            pluginName = item.attributeValue("plugin");
                            String value = getBodyContent().getString();
                            if (value != null) {
                                value = StringUtils.replace(value, "[id]", clean(subitemID));
                                value = StringUtils.replace(value, "[name]",
                                        clean(AdminConsole.getAdminText(subitemName, pluginName)));
                                value = StringUtils.replace(value, "[description]",
                                        clean(AdminConsole.getAdminText(subitemDescr, pluginName)));
                                value = StringUtils.replace(value, "[url]",
                                        request.getContextPath() + "/" + clean(subitemURL));
                            }
                            String css = getCss();
                            boolean isCurrent = item.equals(current);
                            boolean showSubmenu = subPageID != null;
                            if (isCurrent && !showSubmenu) {
                                css = getCurrentcss();
                            }
                            buf.append("<li class=\"").append(css).append("\">").append(value).append("</li>");

                            // Print out a submenu if one exists:
                            if (isSubmenu && isCurrent) {
                                // Get the parent of the current item so we can get its
                                // items - those will be siblings of the current item:
                                Iterator siblings = subcurrent.getParent().elementIterator();
                                boolean hadNext = siblings.hasNext();
                                if (hadNext) {
                                    // Print out beginning UL
                                    buf.append("<li class=\"\"><ul class=\"subitems\">\n");
                                    // Print the header LI
                                    String subheader = subcurrent.getParent().attributeValue("name");
                                    pluginName = subcurrent.getParent().attributeValue("plugin");
                                    buf.append("<li class=\"").append(hcss).append("\">").append(
                                            clean(AdminConsole.getAdminText(subheader, pluginName))).append("</li>");
                                }
                                String extraParams = (String)request.getAttribute("extraParams");
                                while (siblings.hasNext()) {
                                    Element sibling = (Element)siblings.next();
                                    String sibID = sibling.attributeValue("id");
                                    String sibName = sibling.attributeValue("name");
                                    String sibDescr = sibling.attributeValue("description");
                                    String sibURL = sibling.attributeValue("url");
                                    pluginName = sibling.attributeValue("plugin");
                                    if (extraParams != null) {
                                        sibURL += ((sibURL.indexOf('?') > -1 ? "&" : "?") + extraParams);
                                    }
                                    boolean isSubCurrent = sibling.equals(subcurrent);
                                    String subcss = getCss();
                                    if (isSubCurrent) {
                                        subcss = getCurrentcss();
                                    }
                                    String svalue = getSubsidebarTag().getBody();
                                    if (svalue != null) {
                                        svalue = StringUtils.replace(svalue, "[id]", clean(sibID));
                                        svalue = StringUtils.replace(svalue, "[name]",
                                                clean(AdminConsole.getAdminText(sibName, pluginName)));
                                        svalue = StringUtils.replace(svalue, "[description]",
                                                clean(AdminConsole.getAdminText(sibDescr, pluginName)));
                                        svalue = StringUtils.replace(svalue, "[url]",
                                                request.getContextPath() + "/" + clean(sibURL));
                                    }
                                    buf.append("<li class=\"").append(subcss).append("\">").append(svalue).append("</li>\n");
                                }
                                if (hadNext) {
                                    // Print out ending UL
                                    buf.append("</ul></li>\n");
                                }
                            }
                        }
                        buf.append("</ul>");
                        try {
                            out.write(buf.toString());
                        }
                        catch (IOException e) {
                            throw new JspException(e);
                        }
                    }
                }
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