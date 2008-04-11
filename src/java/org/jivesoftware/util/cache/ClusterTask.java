/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
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

