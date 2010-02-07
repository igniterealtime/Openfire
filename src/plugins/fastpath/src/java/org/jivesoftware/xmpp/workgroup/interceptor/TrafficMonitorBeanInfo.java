package org.jivesoftware.xmpp.workgroup.interceptor;

import org.jivesoftware.xmpp.workgroup.utils.WorkgroupBeanInfo;

public class TrafficMonitorBeanInfo extends WorkgroupBeanInfo {

    public static final String[] PROPERTY_NAMES =
            new String[]{"readEnabled", "sentEnabled", "onlyNotProcessedEnabled"};

    @Override
	public String[] getPropertyNames() {
        return PROPERTY_NAMES;
    }

    @Override
	public Class getBeanClass() {
        return TrafficMonitor.class;
    }

    @Override
	public String getName() {
        return "TrafficMonitor";
    }
}
