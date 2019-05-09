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
 * Contains constant values representing various objects in Jive.
 */
public class JiveConstants {

    public static final int SYSTEM = 17;
    public static final int ROSTER = 18;
    public static final int OFFLINE = 19;
    public static final int MUC_ROOM = 23;
    public static final int SECURITY_AUDIT = 25;
    public static final int MUC_SERVICE = 26;
    public static final int MUC_MESSAGE_ID = 27;

    public static final long SECOND = 1000;
    public static final long MINUTE = 60 * SECOND;
    public static final long HOUR = 60 * MINUTE;
    public static final long DAY = 24 * HOUR;
    public static final long WEEK = 7 * DAY;


    /**
     * Date/time format for use by SimpleDateFormat. The format conforms to
     * <a href="http://www.xmpp.org/extensions/xep-0082.html">XEP-0082</a>, which defines
     * a unified date/time format for XMPP.
     *
     * @deprecated Deprecated by the org.jivesoftware.util.XMPPDateTimeFormat class
     * @see org.jivesoftware.util.XMPPDateTimeFormat
     */
    @Deprecated
    public static final String XMPP_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    /**
     * Date/time format for use by SimpleDateFormat. The format conforms to the format
     * defined in <a href="http://www.xmpp.org/extensions/xep-0091.html">XEP-0091</a>,
     * a specialized date format for historical XMPP usage.
     *
     * @deprecated Deprecated by the org.jivesoftware.util.XMPPDateTimeFormat class
     * @see org.jivesoftware.util.XMPPDateTimeFormat
     */
    @Deprecated
    public static final String XMPP_DELAY_DATETIME_FORMAT = "yyyyMMdd'T'HH:mm:ss";
}
