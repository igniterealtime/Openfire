/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;

/**
 * A MBean that exposes an Openfire Statistic.
 */
public class StatisticDynamicMBean implements DynamicMBean
{
    private static final Logger Log = LoggerFactory.getLogger(StatisticDynamicMBean.class);

    private final Statistic stat;

    public StatisticDynamicMBean(Statistic stat)
    {
        this.stat = stat;
    }

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException
    {
        return switch (attribute) {
            case "Name" -> stat.getName();
            case "Description" -> stat.getDescription();
            case "Units" -> stat.getUnits();
            case "Type" -> stat.getStatType().name();
            case "IsPartialSample" -> stat.isPartialSample();
            case "Sample" -> stat.sample();
            default -> throw new AttributeNotFoundException("Unknown attribute: " + attribute);
        };
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException
    {
        // Statistics are read-only.
        throw new AttributeNotFoundException("Attributes are read-only");
    }

    @Override
    public AttributeList getAttributes(String[] attributes)
    {
        AttributeList list = new AttributeList();
        for (String attr : attributes)
        {
            try
            {
                list.add(new Attribute(attr, getAttribute(attr)));
            }
            catch (Exception ignored)
            {
                Log.debug("Exception occurred while trying to get the value of attribute '{}' of statistic '{}'", attr, stat.getName() );
            }
        }
        return list;
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes)
    {
        return new AttributeList(); // Read-only
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature)
    {
        if ("sample".equals(actionName)) {
            return stat.sample();
        }
        return null;
    }

    @Override
    public MBeanInfo getMBeanInfo()
    {
        MBeanAttributeInfo[] attributes = new MBeanAttributeInfo[] {
            new MBeanAttributeInfo("Name", "java.lang.String", "Statistic name", true, false, false),
            new MBeanAttributeInfo("Description", "java.lang.String", "Description of the statistic", true, false, false),
            new MBeanAttributeInfo("Units", "java.lang.String", "Units of measurement", true, false, false),
            new MBeanAttributeInfo("Type", "java.lang.String", "Type of statistic", true, false, false),
            new MBeanAttributeInfo("IsPartialSample", "boolean", "Whether it’s node-local or cluster-wide", true, false, true),
            new MBeanAttributeInfo("Sample", "double", "Current sample value", true, false, false)
        };

        MBeanOperationInfo[] operations = new MBeanOperationInfo[] {
            new MBeanOperationInfo("sample", "Fetches a fresh sample value", null, "double", MBeanOperationInfo.INFO)
        };

        return new MBeanInfo(
            this.getClass().getName(),
            "Dynamic view of org.jivesoftware.openfire.stats.Statistic",
            attributes, null, operations, null
        );
    }
}
