/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.user;

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.JiveGlobals;

/**
 * <p>Provides a centralized source of the various user providers.</p>
 * <p/>
 * <p>The user system has many providers. These providers allow you to
 * integrate Messenger with various backend user management systems on a
 * 'pay as you go' basis. In otherwords, a simple integration only requires
 * a very small amount of customization (modifying a few providers) while
 * a more complex integration can modify many providers.</p>
 * <p/>
 * <p>Users of Jive that wish to change the User*Provider implementation used to generate
 * users can set the <code>UserProvider.*.className</code> Jive properties. For example, if
 * you have altered Jive to use LDAP for user information, you'd want to send a custom
 * implementation of User*Provider classes to make LDAP user queries. After changing the
 * <code>UserProvider.*.className</code> Jive properties, you must restart Messenger. The
 * valid properties are:<p>
 * <p/>
 * <ul>
 * <li>UserProvider.id.className - specifies a UserIDProvider class.</li>
 * <li>UserProvider.properties.className - specifies a UserPropertiesProvider class.</li>
 * <li>UserProvider.info.className - specifies a UserInfoProvider class.</li>
 * <li>UserProvider.account.className - specifies a UserAccountProvider class.</li>
 * </ul>
 *
 * @author Iain Shigeoka
 */
public class UserProviderFactory {

    private static UserIDProvider userIDProvider;
    private static UserPropertiesProvider userPropertiesProvider;
    private static UserInfoProvider userInfoProvider;
    private static UserAccountProvider userAccountProvider;
    private static RosterItemProvider rosterItemProvider;

    /**
     * The default class to instantiate is database implementation.
     */
    private static String[] classNames = {"org.jivesoftware.messenger.user.spi.DbUserIDProvider",
                                          "org.jivesoftware.messenger.user.spi.DbUserPropertiesProvider",
                                          "org.jivesoftware.messenger.user.spi.DbUserInfoProvider",
                                          "org.jivesoftware.messenger.user.spi.DbUserAccountProvider",
                                          "org.jivesoftware.messenger.user.spi.DbRosterItemProvider"};

    private static String[] propNames = {"UserProvider.id.className",
                                         "UserProvider.properties.className",
                                         "UserProvider.info.className",
                                         "UserProvider.account.className",
                                         "UserProvider.roster.className"};

    private static void setProviders(Class[] providers) throws IllegalAccessException, InstantiationException {
        userIDProvider = (UserIDProvider)providers[0].newInstance();
        userPropertiesProvider = (UserPropertiesProvider)providers[1].newInstance();
        userInfoProvider = (UserInfoProvider)providers[2].newInstance();
        userAccountProvider = (UserAccountProvider)providers[3].newInstance();
        rosterItemProvider = (RosterItemProvider)providers[4].newInstance();
    }

    public static UserIDProvider getUserIDProvider() {
        loadProviders();
        return userIDProvider;
    }

    public static UserPropertiesProvider getUserPropertiesProvider() {
        loadProviders();
        return userPropertiesProvider;
    }

    public static UserInfoProvider getUserInfoProvider() {
        loadProviders();
        return userInfoProvider;
    }

    public static UserAccountProvider getUserAccountProvider() {
        loadProviders();
        return userAccountProvider;
    }

    public static RosterItemProvider getRosterItemProvider() {
        loadProviders();
        return rosterItemProvider;
    }

    private static void loadProviders() {
        if (userIDProvider == null) {
            // Use className as a convenient object to get a lock on.
            synchronized (classNames) {
                if (userIDProvider == null) {
                    try {
                        Class[] providers = new Class[classNames.length];
                        for (int i = 0; i < classNames.length; i++) {
                            String className = classNames[i];
                            //See if the classname has been set as a Jive property.
                            String classNameProp = JiveGlobals.getProperty(propNames[i]);
                            if (classNameProp != null) {
                                className = classNameProp;
                            }
                            try {
                                providers[i] = ClassUtils.forName(className);
                            }
                            catch (Exception e) {
                                Log.error("Exception loading class: " + className, e);
                            }
                        }
                        setProviders(providers);
                    }
                    catch (Exception e) {
                        Log.error("Exception loading class: " + classNames, e);
                    }
                }
            }
        }
    }
}
