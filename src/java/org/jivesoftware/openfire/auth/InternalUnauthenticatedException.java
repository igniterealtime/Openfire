/*
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

package org.jivesoftware.openfire.auth;

/**
 * Thrown when Openfire is not able to authenticate itself into the user and group system.
 *
 * @author Gabriel Guardincerri
 */
public class InternalUnauthenticatedException extends Exception {

    public InternalUnauthenticatedException() {
        super();
    }

    public InternalUnauthenticatedException(String message) {
        super(message);
    }

    public InternalUnauthenticatedException(String message, Throwable cause) {
        super(message, cause);
    }

    public InternalUnauthenticatedException(Throwable cause) {
        super(cause);
    }
}
