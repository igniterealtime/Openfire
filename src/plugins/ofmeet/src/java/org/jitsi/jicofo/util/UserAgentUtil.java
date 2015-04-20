/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.util;

import org.jitsi.util.*;

/**
 * @author George Politis
 */
public class UserAgentUtil
{
    public static boolean isFirefox(String userAgent)
    {
        return StringUtils.isNullOrEmpty(userAgent)
            ? false : userAgent.toLowerCase().indexOf("firefox") != -1;
    }
}
