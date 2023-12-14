/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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

import java.io.IOException;

import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.SimpleTagSupport;

public class FlashMessageTag extends SimpleTagSupport {

    public static final String SUCCESS_MESSAGE_KEY = "success";
    public static final String WARNING_MESSAGE_KEY = "warning";
    public static final String ERROR_MESSAGE_KEY = "error";

    @Override
    public void doTag() throws IOException {
        final PageContext pageContext = (PageContext) getJspContext();
        final JspWriter jspWriter = pageContext.getOut();
        final HttpSession session = pageContext.getSession();

        for (final String flash : new String[]{SUCCESS_MESSAGE_KEY, WARNING_MESSAGE_KEY, ERROR_MESSAGE_KEY}) {
            final Object flashValue = session.getAttribute(flash);
            if (flashValue != null) {
                jspWriter.append(String.format("<div class='%s'>%s</div>", flash, flashValue));
                session.setAttribute(flash, null);
            }
        }

    }

}
