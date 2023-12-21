/*
 * Copyright (C) 2017-2022 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.group;

/**
 * Thrown when attempting to set or rename a group, passing an unacceptable name.
 *
 * @author Dan Caseley
 */
public class GroupNameInvalidException extends Exception {


    public GroupNameInvalidException() {
        super();
    }

    public GroupNameInvalidException(String message) {
        super(message);
    }

    public GroupNameInvalidException(Throwable cause) {
        super(cause);
    }

    public GroupNameInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
