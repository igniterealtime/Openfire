/**
 * $RCSfile$
 * $Revision: 4092 $
 * $Date: 2006-06-24 18:58:11 -0400 (Sat, 24 Jun 2006) $
 *
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

package org.jivesoftware.admin;

import org.jivesoftware.util.StringUtils;
import org.dom4j.Element;

import javax.servlet.jsp.tagext.BodyTagSupport;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.io.IOException;

/**
 * <p>A simple JSP tag for displaying sub-navigation bar information in the admin console. The
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
public class SubnavTag extends BodyTagSupport {

    private String css;
    private String currentcss;

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

        // Get the initial subpage and page IDs
        String subPageID = (String)request.getAttribute("subPageID");
        String pageID = (String)request.getAttribute("pageID");

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
                    if (subcurrent != null) {
                        subnav = subcurrent.getParent().getParent().getParent();
                    }
                    else {
                        subnav = current.getParent();
                    }
                }

                Element currentTab = (Element)AdminConsole.getModel().selectSingleNode(
                        "//*[@id='" + pageID + "']/ancestor::tab");

                // Loop through all items in the root, print them out
                if (currentTab != null) {
                    Collection items = currentTab.elements();
                    if (items.size() > 0) {
                        buf.append("<ul>");
                        for (Object itemObj : items) {
                            Element item = (Element) itemObj;
                            if (item.elements().size() > 0) {
                                Element firstSubItem = (Element)item.elements().get(0);
                                String pluginName = item.attributeValue("plugin");
                                String subitemID = item.attributeValue("id");
                                String subitemName = item.attributeValue("name");
                                String subitemURL = firstSubItem.attributeValue("url");
                                String subitemDescr = item.attributeValue("description");
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
                                boolean isCurrent = subnav != null && item.equals(subnav);
                                if (isCurrent) {
                                    css = getCurrentcss();
                                }
                                buf.append("<li class=\"").append(css).append("\">").append(value).append("</li>");
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