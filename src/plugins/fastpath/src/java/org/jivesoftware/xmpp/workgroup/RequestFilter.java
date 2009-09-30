/**
 * $RCSfile$
 * $Revision: 18406 $
 * $Date: 2005-02-07 14:32:46 -0800 (Mon, 07 Feb 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.xmpp.workgroup;

import org.jivesoftware.xmpp.workgroup.request.Request;
import org.xmpp.packet.PacketError;

/**
 * <p>Allows systems to reject a users request based on arbitrary criteria.</p>
 * <p>Implementations of the request filter may decide to filter based on meta-data,
 * the sender of the request, time of day, etc.</p>
 *
 * @author Derek DeMoro
 */
public interface RequestFilter {
    /**
     * <p>Return the error to respond to the request with, or XMPPError.NONE
     * if the request should be allowed to join the workgroup.</p>
     *
     * <p>The request passed in to filter(s) is the request being considered.
     * Filters may decide to alter the request when processing it in order to
     * affect downstream filters in cases where multiple filters are used. If
     * the request needs to be altered by the filter, but these changes should
     * not affect the workgroup or other filters, you should make a copy of the
     * request to work with.</p>
     *
     * @param request The request to be evaluated.
     * @return The error code or XMPPError.NONE if the request should be allowed
     */
    PacketError.Condition filter(Request request);
}

