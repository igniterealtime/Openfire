/**
 * $RCSfile$
 * $Revision$
 * $Date$
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

import java.util.Collection;
import java.util.ArrayList;

/**
 * A bean to hold page information for the admin console.
 */
public class AdminPageBean {

    private String title;
    private Collection breadcrumbs;
    private String pageID;
    private String subPageID;
    private String extraParams;
    private Collection scripts;

    public AdminPageBean() {
    }

    /**
     * Returns the title of the page with HTML escaped.
     */
    public String getTitle() {
        if (title != null) {
            return StringUtils.escapeHTMLTags(title);
        }
        else {
            return title;
        }
    }

    /**
     * Sets the title of the admin console page.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns a collection of breadcrumbs. Use the Collection API to get/set/remove crumbs.
     */
    public Collection getBreadcrumbs() {
        if (breadcrumbs == null) {
            breadcrumbs = new ArrayList();
        }
        return breadcrumbs;
    }

    /**
     * Returns the page ID (corresponds to sidebar ID's).
     */
    public String getPageID() {
        return pageID;
    }

    /**
     * Sets the ID of the page (corresponds to sidebar ID's).
     * @param pageID
     */
    public void setPageID(String pageID) {
        this.pageID = pageID;
    }

    /**
     * Returns the subpage ID (corresponds to sidebar ID's).
     */
    public String getSubPageID() {
        return subPageID;
    }

    /**
     * Sets the subpage ID (corresponds to sidebar ID's).
     * @param subPageID
     */
    public void setSubPageID(String subPageID) {
        this.subPageID = subPageID;
    }

    /**
     * Returns a string of extra parameters for the URLs - these might be specific IDs for resources.
     */
    public String getExtraParams() {
        return extraParams;
    }

    /**
     * Sets the string of extra parameters for the URLs.
     */
    public void setExtraParams(String extraParams) {
        this.extraParams = extraParams;
    }

    /**
     * Returns a collection of scripts. Use the Collection API to get/set/remove scripts.
     */
    public Collection getScripts() {
        if (scripts == null) {
            scripts = new ArrayList();
        }
        return scripts;
    }

    /**
     * A simple model of a breadcrumb. A bread crumb is a link with a display name.
     */
    public static class Breadcrumb {
        private String name;
        private String url;

        /**
         * Creates a crumb given a name an URL.
         */
        public Breadcrumb(String name, String url) {
            this.name = name;
            this.url = url;
        }

        /**
         * Returns the name, with HTML escaped.
         */
        public String getName() {
            if (name != null) {
                return StringUtils.escapeHTMLTags(name);
            }
            else {
                return name;
            }
        }

        /**
         * Returns the URL.
         */
        public String getUrl() {
            return url;
        }
    }
}
