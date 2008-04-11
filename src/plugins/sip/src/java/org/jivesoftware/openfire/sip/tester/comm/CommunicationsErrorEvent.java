/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.sip.tester.comm;

import java.util.EventObject;

/**
 * <p/>
 * Title: SIP COMMUNICATOR-1.1
 * </p>
 * <p/>
 * Description: JAIN-SIP-1.1 Audio/Video Phone Application
 * </p>
 * <p/>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p/>
 * Company: Organisation: LSIIT Laboratory (http://lsiit.u-strasbg.fr) \nNetwork
 * Research Team (http://www-r2.u-strasbg.fr))\nLouis Pasteur University -
 * Strasbourg - France
 * </p>
 *
 * @author Emil Ivov
 * @version 1.1
 */
public class CommunicationsErrorEvent extends EventObject {
    public CommunicationsErrorEvent(Throwable source) {
        super(source);
    }

    public Throwable getCause() {
        return (Throwable)source;
    }
}
