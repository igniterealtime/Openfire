/**
 * $RCSfile$
 * $Revision: 691 $
 * $Date: 2004-12-13 15:06:54 -0300 (Mon, 13 Dec 2004) $
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

package org.jivesoftware.openfire.clearspace;

/**
 * Thrown when an exception occurs connecting to CS.
 */
public class ConnectionException extends Exception {

    public enum ErrorType {AUTHENTICATION, PAGE_NOT_FOUND, UPDATE_STATE, UNKNOWN_HOST, SERVICE_NOT_AVAIBLE, OTHER};

    private ErrorType errorType;

    public ConnectionException(String s, Throwable throwable, ErrorType errorType) {
        super(s, throwable);
        this.errorType = errorType;
    }

    public ConnectionException(String s, ErrorType errorType) {
        super(s);
        this.errorType = errorType;
    }

    public ConnectionException(Throwable throwable, ErrorType errorType) {
        super(throwable);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

}
