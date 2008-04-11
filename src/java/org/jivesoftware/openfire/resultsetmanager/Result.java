/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.resultsetmanager;

/**
 * Elements from a result set as defined by XEP-0059 have certain
 * characteristics. This interface defines these characteristics.
 * 
 * Applying this interface to a class will allow you to use ResultSet operations
 * on collections of your class. In other words: you are making collections of
 * your class managable/navigable.
 * 
 * @author Guus der Kinderen, guus@nimbuzz.com
 * @see <a href="http://www.xmpp.org/extensions/xep-0059.html">XEP-0059: Result Set Management</a>
 */
public interface Result {

	/**
	 * Returns a unique identifier for this Result. Each element in a ResultSet
	 * must have a distinct UIDs. 
	 * 
	 * XEP-0059 says: <quote>(...) the UIDs are
	 * unique in the context of all possible members of the full result set.
	 * Each UID MAY be based on part of the content of its associated item (...)
	 * or on an internal table index. Another possible method is to serialize
	 * the XML of the item and then hash it to generate the UID. Note: The
	 * requesting entity MUST treat all UIDs as opaque.</quote>
	 * 
	 * @return Unique ID of the Result
	 */
	public String getUID();

}