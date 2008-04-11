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

import org.jivesoftware.openfire.sip.tester.stack.RegistrationEvent;

import java.util.EventListener;

/**
 * <p/>
 * Title: SIP COMMUNICATOR
 * </p>
 * <p/>
 * Description:JAIN-SIP Audio/Video phone application
 * </p>
 * <p/>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p/>
 * Organisation: LSIIT laboratory (http://lsiit.u-strasbg.fr)
 * </p>
 * <p/>
 * Network Research Team (http://www-r2.u-strasbg.fr))
 * </p>
 * <p/>
 * Louis Pasteur University - Strasbourg - France
 * </p>
 *
 * @author Emil Ivov (http://www.emcho.com)
 * @version 1.1
 */
public interface CommunicationsListener extends EventListener {

    public void registered(RegistrationEvent evt);

    public void registering(RegistrationEvent evt);

    public void registrationFailed(RegistrationEvent evt);

    public void unregistering(RegistrationEvent evt);

    public void unregistered(RegistrationEvent evt);

}
