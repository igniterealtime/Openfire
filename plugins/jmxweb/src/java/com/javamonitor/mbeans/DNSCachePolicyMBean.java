package com.javamonitor.mbeans;

import java.lang.reflect.InvocationTargetException;

/**
 * The interface to the DNS cache policy mbean. We use this interface to expose
 * two pieces of information about the cache policy. First, we want to know what
 * the actual cache policy is, i.e. how long DNS queries are cached for.
 * <p>
 * Second, we want to know if the administrator tweaked the DNS cache policy at
 * all. This is useful to supress helpful advise on tweaking the policy in cases
 * where the administrator deliberately chose to use an extreme policy.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public interface DNSCachePolicyMBean {
    /**
     * Find out how long successful DNS queries are cached for. 'Successful' in
     * this context means queries that yielded an IP address.
     * 
     * @return The number of seconds a successful DNS lookup is cached.
     * @throws ClassNotFoundException
     *             When the policy inspector class could not be loaded.
     * @throws IllegalAccessException
     *             When the policy inspector could not be queried.
     * @throws InvocationTargetException
     *             When the policy inspector could not be queried.
     * @throws NoSuchMethodException
     *             When the policy inspector could not be queried.
     */
    int getCacheSeconds() throws ClassNotFoundException,
            IllegalAccessException, InvocationTargetException,
            NoSuchMethodException;

    /**
     * Find out if the cache policy for successful DNS lookups was changed from
     * the hard-coded default or not.
     * 
     * @return "default" if this value is the built-in default, "security" if
     *         this property is set as a security property, "system" if it was
     *         set as a system property or "both" if it was set as both a system
     *         and a security property. The latter is probably a configuration
     *         error.
     */
    String getCacheTweakedFrom();

    /**
     * Find out how long failed DNS queries are cached for. 'Failed' in this
     * context means DNS lookups that resulted in an error that the specified
     * name does not exist.
     * 
     * @return The number of seconds a failed DNS lookup is cached.
     * @throws ClassNotFoundException
     *             When the policy inspector class could not be loaded.
     * @throws IllegalAccessException
     *             When the policy inspector could not be queried.
     * @throws InvocationTargetException
     *             When the policy inspector could not be queried.
     * @throws NoSuchMethodException
     *             When the policy inspector could not be queried.
     */
    int getCacheNegativeSeconds() throws ClassNotFoundException,
            IllegalAccessException, InvocationTargetException,
            NoSuchMethodException;

    /**
     * Find out if the cache policy for failed DNS lookups was changed from the
     * hard-coded default or not.
     * <p>
     * Sun JVMs ship with this property set from the system's security policy
     * file.
     * 
     * @return "default" if this value is the built-in default, "security" if
     *         this property is set as a security property, "system" if it was
     *         set as a system property or "both" if it was set as both a system
     *         and a security property. The latter is probably a configuration
     *         error.
     */
    String getCacheNegativeTweakedFrom();
}
