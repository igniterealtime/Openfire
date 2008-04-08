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
