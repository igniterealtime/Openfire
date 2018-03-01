/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * @deprecated Replaced by {@link org.xmpp.resultsetmanagement.Result}
 */
@Deprecated
public interface Result {

    /**
     * Returns a unique identifier for this Result. Each element in a ResultSet
     * must have a distinct UIDs. 
     * 
     * XEP-0059 says: <i>(...) the UIDs are
     * unique in the context of all possible members of the full result set.
     * Each UID MAY be based on part of the content of its associated item (...)
     * or on an internal table index. Another possible method is to serialize
     * the XML of the item and then hash it to generate the UID. Note: The
     * requesting entity MUST treat all UIDs as opaque.</i>
     * 
     * @return Unique ID of the Result
     */
    String getUID();

}
