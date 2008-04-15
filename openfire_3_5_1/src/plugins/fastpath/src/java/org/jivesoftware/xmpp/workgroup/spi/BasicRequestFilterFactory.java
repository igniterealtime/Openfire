/**
 * $RCSfile$
 * $Revision: 18112 $
 * $Date: 2004-11-22 18:23:25 -0800 (Mon, 22 Nov 2004) $
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.xmpp.workgroup.spi;

import org.jivesoftware.xmpp.workgroup.RequestFilter;
import org.jivesoftware.xmpp.workgroup.RequestFilterFactory;

public class BasicRequestFilterFactory extends RequestFilterFactory {
    RequestFilter filter = new BasicRequestFilter();

    public RequestFilter getFilter() {
        return filter;
    }
}
