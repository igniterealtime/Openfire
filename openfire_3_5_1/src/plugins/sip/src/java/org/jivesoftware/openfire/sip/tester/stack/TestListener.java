/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sip.tester.stack;

/**
 * Title: SIP Register Tester
 *
 * @author Thiago Rocha Camargo (thiago@jivesoftware.com)
 */

public interface TestListener {
    public void resultChanged(SIPTest.Result old, SIPTest.Result current);
}
