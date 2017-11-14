package org.jivesoftware.xmpp.workgroup.spi.dispatcher;

import org.jivesoftware.xmpp.workgroup.utils.WorkgroupBeanInfo;


public class BasicAgentSelectorBeanInfo extends WorkgroupBeanInfo {

    public static final String[] PROPERTY_NAMES = new String[]{};

    @Override
	public String[] getPropertyNames() {
        return PROPERTY_NAMES;
    }

    @Override
	public Class getBeanClass() {
        return BasicAgentSelector.class;
    }

    @Override
	public String getName() {
        return "BasicAgentSelector";
    }
}
