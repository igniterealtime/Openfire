/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.container;

import java.io.Serializable;

/**
 * A portable data object used to conduct server searches and for
 * temporary data storage in a Jive container. Entries must have
 * a no-arg constructor. All fields must be a public Object (no
 * primitives or private fields allowed). This allows quicker searching
 * and comparisons of Entry objects. Entry objects that don't comply
 * with these rules will cause an UnusableEntryException to be thrown
 * by most services.
 *
 * @author Iain Shigeoka
 */
public interface Entry extends Serializable {
}
