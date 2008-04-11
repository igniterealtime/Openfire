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

package org.jivesoftware.openfire.net;

import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * @author Daniele Piras
 */
interface SelectorAction
{
  public abstract void read( SelectionKey key ) throws IOException;
  public abstract void connect( SelectionKey key ) throws IOException;
}
