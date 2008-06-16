/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.util.cache;

import java.io.Externalizable;

/**
 * An interface to mix in Serializable and Runnable, which are both required for
 * sending invocable tasks across a cluster.
 */
public interface ClusterTask extends Runnable, Externalizable {

    public Object getResult();

}

