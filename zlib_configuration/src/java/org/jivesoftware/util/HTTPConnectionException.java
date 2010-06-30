/**
 * $RCSfile$
 * $Revision$
 * $Date$
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

package org.jivesoftware.util;

/**
 * Exception class that wraps an HTTP error code.
 *
 * @author Gaston Dombiak
 */
public class HTTPConnectionException extends Exception {

    private int errorCode;

    public HTTPConnectionException(int errorCode) {
        super();
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }

    @Override
	public String getMessage() {
        if (errorCode == 400) {
            return "400 Bad Request";
        }
        else if (errorCode == 401) {
            return "401 Unauthorized";
        }
        else if (errorCode == 402) {
            return "402 Payment Required";
        }
        else if (errorCode == 403) {
            return "403 Forbidden";
        }
        else if (errorCode == 404) {
            return "404 Not Found";
        }
        else if (errorCode == 405) {
            return "405 Method Not Allowed";
        }
        else if (errorCode == 406) {
            return "406 Not Acceptable";
        }
        else if (errorCode == 407) {
            return "407 Proxy Authentication Required";
        }
        else if (errorCode == 408) {
            return "408 Request Timeout";
        }
        else if (errorCode == 409) {
            return "409 Conflict";
        }
        else if (errorCode == 410) {
            return "410 Gone";
        }
        else if (errorCode == 411) {
            return "411 Length Required";
        }
        else if (errorCode == 412) {
            return "412 Precondition Failed";
        }
        else if (errorCode == 413) {
            return "413 Request Entity Too Large";
        }
        else if (errorCode == 414) {
            return "414 Request-URI Too Long";
        }
        else if (errorCode == 415) {
            return "415 Unsupported Media Type";
        }
        else if (errorCode == 416) {
            return "416 Requested Range Not Satisfiable";
        }
        else if (errorCode == 418) {
            return "417 Expectation Failed";
        }
        return "Unknown HTTP error code: " + errorCode; 
    }
}
