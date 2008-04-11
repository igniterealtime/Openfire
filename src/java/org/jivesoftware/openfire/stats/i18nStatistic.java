/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.stats;

import org.jivesoftware.util.LocaleUtils;

/**
 *  A convience class to build statistic parameters out of a resource bundle.
 *
 * @author Alexander Wenckus
 */
public abstract class i18nStatistic implements Statistic {

    private String resourceKey;
    private String pluginName;
    private Type statisticType;

    public i18nStatistic(String resourceKey, Statistic.Type statisticType) {
        this(resourceKey, null, statisticType);
    }

    public i18nStatistic(String resourceKey, String pluginName, Statistic.Type statisticType) {
        this.resourceKey = resourceKey;
        this.pluginName = pluginName;
        this.statisticType = statisticType;
    }

    public final String getName() {
        return retrieveValue("name");
    }

    public final Type getStatType() {
        return statisticType;
    }

    public final String getDescription() {
        return retrieveValue("desc");
    }

    public final String getUnits() {
        return retrieveValue("units");
    }

    private String retrieveValue(String key) {
        String wholeKey = "stat." + resourceKey + "." + key;
        if (pluginName != null) {
            return LocaleUtils.getLocalizedString(wholeKey, pluginName);
        }
        else {
            return LocaleUtils.getLocalizedString(wholeKey);
        }
    }
}
