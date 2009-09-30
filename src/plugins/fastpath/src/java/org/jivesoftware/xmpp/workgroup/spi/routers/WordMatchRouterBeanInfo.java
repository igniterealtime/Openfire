/**
 * $RCSfile$
 * $Revision: 19264 $
 * $Date: 2005-07-08 15:30:34 -0700 (Fri, 08 Jul 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.xmpp.workgroup.spi.routers;

import org.jivesoftware.xmpp.workgroup.utils.WorkgroupBeanInfo;


public class WordMatchRouterBeanInfo extends WorkgroupBeanInfo {

    public static final String[] PROPERTY_NAMES =
            new String[]{"keyName", "words", "stemmingEnabled"};

    public WordMatchRouterBeanInfo() {
        super();
    }

    public Class getBeanClass() {
        return org.jivesoftware.xmpp.workgroup.spi.routers.WordMatchRouter.class;
    }

    public String[] getPropertyNames() {
        return PROPERTY_NAMES;
    }

    public String getName() {
        return "WordMatchRouter";
    }
}

