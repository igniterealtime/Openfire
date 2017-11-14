package org.jivesoftware.xmpp.workgroup.interceptor;

import org.jivesoftware.xmpp.workgroup.utils.WorkgroupBeanInfo;

/**
 * BeanInfo class for the user interceptor.
 *
 * @author Gaston Dombiak
 */
public class UserInterceptorBeanInfo extends WorkgroupBeanInfo {

    public static final String[] PROPERTY_NAMES = new String[]{"jidBanList", "domainBanList",
                                                               "emailNotifyList", "fromEmail",
                                                               "fromName", "emailSubject",
                                                               "emailBody", "rejectionMessage"};

    public UserInterceptorBeanInfo() {
        super();
    }

    @Override
	public Class getBeanClass() {
        return UserInterceptor.class;
    }

    @Override
	public String[] getPropertyNames() {
        return PROPERTY_NAMES;
    }

    @Override
	public String getName() {
        return "UserInterceptor";
    }
}
