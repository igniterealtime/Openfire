/*
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

    @Override
    public final String getName() {
        return retrieveValue("name");
    }

    @Override
    public final Type getStatType() {
        return statisticType;
    }

    @Override
    public final String getDescription() {
        return retrieveValue("desc");
    }

    @Override
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
