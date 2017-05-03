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

package org.jivesoftware.util;

/**
 * Exception thrown when a modification was not allowed.
 */
public class ModificationNotAllowedException extends Exception {
    public ModificationNotAllowedException() {
        super();
    }

    public ModificationNotAllowedException(String message) {
        super(message);
    }

    public ModificationNotAllowedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModificationNotAllowedException(Throwable cause) {
        super(cause);
    }
}
