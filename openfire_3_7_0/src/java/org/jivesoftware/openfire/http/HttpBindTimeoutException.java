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

package org.jivesoftware.openfire.http;

/**
 * An exception which indicates that the maximum waiting time for a client response has been
 * surpassed and an empty response should be returned to the requesting client.
 *
 * @author Alexander Wenckus
 */
class HttpBindTimeoutException extends Exception {
    public HttpBindTimeoutException(String message) {
        super(message);
    }

    public HttpBindTimeoutException() {
        super();
    }
}
