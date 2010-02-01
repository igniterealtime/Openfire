/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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

package org.jivesoftware.openfire.sip.tester.comm;

import java.util.EventObject;

/**
 * <p/>
 * Title: SIP COMMUNICATOR-1.1
 * </p>
 * <p/>
 * Description: JAIN-SIP-1.1 Audio/Video Phone Application
 * </p>
 * <p/>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p/>
 * Company: Organisation: LSIIT Laboratory (http://lsiit.u-strasbg.fr) \nNetwork
 * Research Team (http://www-r2.u-strasbg.fr))\nLouis Pasteur University -
 * Strasbourg - France
 * </p>
 *
 * @author Emil Ivov
 * @version 1.1
 */
public class CommunicationsErrorEvent extends EventObject {
    public CommunicationsErrorEvent(Throwable source) {
        super(source);
    }

    public Throwable getCause() {
        return (Throwable)source;
    }
}
