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

package org.jivesoftware.openfire.sip.log;

import org.xmpp.component.ComponentManager;
import org.xmpp.packet.IQ;

/**
 * Interface for Log listening
 *
 */
public interface LogListener {

	public IQ logReceived(IQ iq);

	public ComponentManager getComponentManager();

}
