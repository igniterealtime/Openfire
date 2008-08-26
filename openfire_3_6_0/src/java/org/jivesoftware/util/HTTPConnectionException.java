/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
