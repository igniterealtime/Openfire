/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.auth;

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.JiveGlobals;

/**
 * <p>Provides a centralized source of the various auth providers.</p>
 * <p/>
 * <p>The auth system has one provider. The provider allows you to
 * integrate Messenger with various backend authenication systems without
 * necessarily replacing the Messenger user management system.
 * In other words, you can verify usernames and passwords against a common
 * user directory, but allow messenger to manage copies of the user account
 * data in it's own database. This results in a redundant storage of data
 * and can cause 'data skew' where values are not updated in sync. However,
 * it is the simplest and least amount of work to integrate Messenger with
 * existing authentication systems.</p>
 * <p/>
 * <p>Users of Jive that wish to change the AuthProvider implementation
 * used to generate users can set the <code>AuthProvider.className</code>
 * Jive property. For example, if you have altered Jive to use LDAP for
 * user information, you'd want to send a custom implementation of
 * AuthProvider classes to make LDAP authetnication queries. After changing the
 * <code>AuthProvider.className</code> Jive property, you must restart
 * Messenger. The valid properties are:<p>
 * <p/>
 * <ul>
 * <li>AuthProvider.className - specifies an AuthProvider class.</li>
 * <li>GroupProvider.className - specifies a GroupProvider class.</li>
 * </ul>
 *
 * @author Iain Shigeoka
 */
public class AuthProviderFactory {
    /**
     * The default class to instantiate is database implementation.
     */
    private static String authClassName =
            "org.jivesoftware.messenger.auth.spi.DbAuthProvider";
    private static String groupClassName =
            "org.jivesoftware.messenger.auth.spi.DbGroupProvider";

    private static AuthProvider authProvider = null;
    private static GroupProvider groupProvider = null;

    /**
     * <p>Obtains a concrete AuthProvider.<p>
     * <p/>
     * <p>By default, the implementation used will be an instance
     * of DbAuthProvider -- the standard database implementation
     * that uses the Jive user table. A different authProvider can be
     * specified by setting the Jive property "AuthProvider.className".
     * However, you must restart Jive for any change to take effect.</p>
     */
    public static AuthProvider getAuthProvider() {
        if (authProvider == null) {
            // Use className as a convenient object to get a lock on.
            synchronized (authClassName) {
                if (authProvider == null) {
                    //See if the classname has been set as a Jive property.
                    String classNameProp =
                            JiveGlobals.getJiveProperty("AuthProvider.className");
                    if (classNameProp != null) {
                        authClassName = classNameProp;
                    }
                    try {
                        Class c = ClassUtils.forName(authClassName);
                        authProvider = (AuthProvider)c.newInstance();
                    }
                    catch (Exception e) {
                        Log.error("Exception loading class: " + authClassName, e);
                    }
                }
            }
        }
        return authProvider;
    }

    /**
     * <p>Obtains a concrete GroupProvider.<p>
     * <p/>
     * <p>By default, the implementation used will be an instance
     * of DbGroupProvider -- the standard database implementation
     * that uses the Jive group table. A different GroupProvider can be
     * specified by setting the Jive property "GroupProvider.className".
     * However, you must restart Jive for any change to take effect.</p>
     */
    public static GroupProvider getGroupProvider() {
        if (groupProvider == null) {
            // Use className as a convenient object to get a lock on.
            synchronized (groupClassName) {
                if (groupProvider == null) {
                    //See if the classname has been set as a Jive property.
                    String classNameProp =
                            JiveGlobals.getJiveProperty("GroupProvider.className");
                    if (classNameProp != null) {
                        groupClassName = classNameProp;
                    }
                    try {
                        Class c = ClassUtils.forName(groupClassName);
                        groupProvider = (GroupProvider)c.newInstance();
                    }
                    catch (Exception e) {
                        Log.error("Exception loading class: " + groupClassName, e);
                    }
                }
            }
        }
        return groupProvider;
    }
}
