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

package org.jivesoftware.admin;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;

/**
 * <p>A simple tag to gather its body content and pass it to the parent tag. This tag must be used
 * as a child of the {@link SidebarTag}.</p>
 *
 * <p>Sample usage:</p><pre>{@code
 *
 *      &lt;jive:sidebar bean="jivepageinfo"&gt; <br>
 *          &nbsp;&nbsp;&nbsp;&lt;a href="[url]" title="[description]"&gt;[name]&lt;/a&gt; <br>
 *          &nbsp;&nbsp;&nbsp;&lt;jive:subsidebar&gt; <br>
 *          &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;a href="[url]"&gt;[name]&lt;/a&gt; <br>
 *          &nbsp;&nbsp;&nbsp;&lt;/jive:subsidebar&gt; <br>
 *      &lt;/jive:sidebar&gt;}</pre>
 *
 * <p>Note, this class has no attributes.</p>
 */
public class SubSidebarTag extends SidebarTag {

    private SidebarTag parent;
    private String body;

    /**
     * Returns the body content of this tag.
     * @return the body of the tag
     */
    public String getBody() {
        return body;
    }

    /**
     * Sets the body content of this tag.
     * @param body the body of this tag
     */
    public void setBody(String body) {
        this.body = body;
    }

    /**
     * Looks for the parent SidebarTag class, throws an error if not found. If found,
     * {@link #EVAL_BODY_BUFFERED} is returned.
     *
     * @return {@link #EVAL_BODY_BUFFERED} if no errors.
     * @throws javax.servlet.jsp.JspException if a parent SidebarTag is not found.
     */
    @Override
    public int doStartTag() throws JspException {
        // The I18nTag should be our parent Tag
        parent = (SidebarTag)findAncestorWithClass(this, SidebarTag.class);

        // If I18nTag was not our parent, throw Exception
        if (parent == null) {
            throw new JspTagException("SubSidebarTag with out a parent which is expected to be a SidebarTag");
        }
        return EVAL_BODY_BUFFERED;
    }

    /**
     * Sets the 'body' property to be equal to the body content of this tag. Calls the
     * {@link SidebarTag#setSubSidebar(SubSidebarTag)} method of the parent tag.
     * @return {@link #EVAL_PAGE}
     * @throws JspException if an error occurs.
     */
    @Override
    public int doEndTag() throws JspException {
        setBody(bodyContent.getString());
        parent.setSubSidebar(this);
        return EVAL_PAGE;
    }
}
