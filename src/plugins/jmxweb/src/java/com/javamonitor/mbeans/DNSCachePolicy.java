package com.javamonitor.mbeans;

import static com.javamonitor.JmxHelper.objectNameBase;

import java.lang.reflect.InvocationTargetException;
import java.security.Security;

/**
 * An implementation of the DNS cache policy MBean interface for Sun JVM's.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public class DNSCachePolicy implements DNSCachePolicyMBean {
    /**
     * The object name for the threading helper mbean.
     */
    public static final String objectName = objectNameBase + "DNSCachePolicy";

    private static final String POLICY = "sun.net.InetAddressCachePolicy";

    /**
     * @see com.javamonitor.mbeans.DNSCachePolicyMBean#getCacheSeconds()
     */
    @SuppressWarnings("unchecked")
    public int getCacheSeconds() throws ClassNotFoundException,
            IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        final Class policy = Class.forName(POLICY);
        final Object returnValue = policy.getMethod("get", (Class[]) null)
                .invoke(null, (Object[]) null);
        final Integer seconds = (Integer) returnValue;

        return seconds.intValue();
    }

    /**
     * @see com.javamonitor.mbeans.DNSCachePolicyMBean#getCacheNegativeSeconds()
     */
    @SuppressWarnings("unchecked")
    public int getCacheNegativeSeconds() throws ClassNotFoundException,
            IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        final Class policy = Class.forName(POLICY);
        final Object returnValue = policy.getMethod("getNegative",
                (Class[]) null).invoke(null, (Object[]) null);
        final Integer seconds = (Integer) returnValue;

        return seconds.intValue();
    }

    private static final String DEFAULT = "default";

    private static final String SECURITY = "security";

    private static final String SYSTEM = "system";

    private static final String BOTH = "both";

    private static final String SECURITY_TTL = "networkaddress.cache.ttl";

    private static final String SYSTEM_TTL = "sun.net.inetaddr.ttl";

    private static final String SECURITY_NEGATIVE_TTL = "networkaddress.cache.negative.ttl";

    private static final String SYSTEM_NEGATIVE_TTL = "sun.net.inetaddr.negative.ttl";

    /**
     * @see com.javamonitor.mbeans.DNSCachePolicyMBean#getCacheTweakedFrom()
     */
    public String getCacheTweakedFrom() {
        if (Security.getProperty(SECURITY_TTL) != null) {
            if (System.getProperty(SYSTEM_TTL) != null) {
                return BOTH;
            }

            return SECURITY;
        }

        if (System.getProperty(SYSTEM_TTL) != null) {
            return SYSTEM;
        }

        return DEFAULT;
    }

    /**
     * @see com.javamonitor.mbeans.DNSCachePolicyMBean#getCacheNegativeTweakedFrom()
     */
    public String getCacheNegativeTweakedFrom() {
        if (Security.getProperty(SECURITY_NEGATIVE_TTL) != null) {
            if (System.getProperty(SYSTEM_NEGATIVE_TTL) != null) {
                return BOTH;
            }

            return SECURITY;
        }

        if (System.getProperty(SYSTEM_NEGATIVE_TTL) != null) {
            return SYSTEM;
        }

        return DEFAULT;
    }
}
