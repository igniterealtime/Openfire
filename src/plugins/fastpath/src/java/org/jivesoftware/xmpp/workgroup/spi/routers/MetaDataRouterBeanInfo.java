/**
 * $RCSfile$
 * $Revision: 19264 $
 * $Date: 2005-07-08 15:30:34 -0700 (Fri, 08 Jul 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.spi.routers;

import org.jivesoftware.xmpp.workgroup.utils.WorkgroupBeanInfo;

public class MetaDataRouterBeanInfo extends WorkgroupBeanInfo {

    public static final String[] PROPERTY_NAMES =
        new String[]{};

    public MetaDataRouterBeanInfo() {
        super();
    }

    public Class getBeanClass() {
        return org.jivesoftware.xmpp.workgroup.spi.routers.MetaDataRouter.class;
    }

    public String[] getPropertyNames() {
        return PROPERTY_NAMES;
    }

    public String getName() {
        return "MetaDataRouter";
    }
}

