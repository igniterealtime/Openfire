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

package org.jivesoftware.messenger.container;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>A skeleton Module implementation that takes care of the most
 * common Module housekeeping chores.</p>
 * <p>The basic module manages the lookup registration for the
 * service but requires a small amount of help. In order to know
 * what services should be registered, the basic module will go
 * through the following during start()</p>
 * <ul>
 * <li>Call getServices(). If the result is not null, all services
 * in the list are registered.</li>
 * <li>Call getService(). If the result is not null, the service
 * is registered.</li>
 * <li>Call getServiceItemss(). If the result is not null, all service items
 * in the list are registered.</li>
 * <li>Call getServiceItem(). If the result is not null, the service item
 * is registered.</li>
 * </ul>
 * <p>It is critical that inheriting classes overriding the module
 * lifecycle methods call the parent life cycle
 * methods in order to ensure these housekeeping tasks occur.</p>
 * <p/>
 * <h2>Tracker</h2>
 * <p>The basic module can manage a ServiceTracker for inheriting classes. To
 * use the built-in tracker, you must do the following:</p>
 * <ol>
 * <li>Override getTrackInfo() - and return an array of classes that you want
 * the service tracker to track.</li>
 * <li>Override addService() and removeService() - to receive services that are
 * being added or removed from the lookup. It is important to react to both.
 * Removal should result in the removal of any references to the service object
 * so the garbage collector can clean up the service. It is also dangerous
 * (e.g. throw NPE) to use services that have been removed from the lookup.</li>
 * </ol>
 *
 * @author Iain Shigeoka
 */
public class BasicModule implements Module {

    /**
     * The name of the module
     */
    private String name;

    /**
     * <p>Create a basic module with the given name.</p>
     *
     * @param moduleName The name for the module or null to use the default
     */
    public BasicModule(String moduleName) {
        if (moduleName == null) {
            this.name = "No name assigned";
        }
        else {
            this.name = moduleName;
        }
    }

    // #####################################################################
    // Override these methods to adjust the basic module behavior
    // #####################################################################

    /**
     * <p>Return the services that this module implements.</p>
     * <p>The basic module will register, and unregister these if not null</p>
     *
     * @return The services to register, or null if there are no services
     */
    protected Object[] getServices() {
        return null;
    }

    /**
     * <p>Return the service that this module implements.</p>
     * <p>The basic module will register, and unregister the service if not null.
     * By default, basic module will register the module class in the lookup.</p>
     *
     * @return The service to register, or null if there is no service
     */
    protected Object getService() {
        return this;
    }

    /**
     * <p>Return the service items that this module implements.</p>
     * <p>The basic module will register, and unregister these if not null</p>
     *
     * @return The service items to register, or null if there are no service items
     */
    protected ServiceItem[] getServiceItems() {
        return null;
    }

    /**
     * <p>Return the service item that this module implements.</p>
     * <p>The basic module will register, and unregister the service item if not null</p>
     *
     * @return The service item to register, or null if there is no service item
     */
    protected ServiceItem getServiceItem() {
        return null;
    }

    /**
     * <p>Override to use the built-in tracker and return the classes to be tracked.</p>
     * <p>The tracker handles the possibly complex tasks of tracked services as they
     * are registered and removed from the service lookup. For maximum flexibility while
     * retaining ease of use, the basic module tries to manage the entire process for
     * inheriting classes. The only responsibility of inheriting classes is to properly
     * implement this method to return the correct tracker class information. However,
     * if an inheriting class wishes to react to the adding and removal of services, they
     * should override the serviceAdded() and serviceRemoved() methods.</p>
     * <p/>
     * <p>The tracker class map should contain:</p>
     * <ul>
     * <li>Key: (Class) tracked class type - The class of the service to be tracked.</li>
     * <li>Value: (String) field name or (String[]) array of field names -
     * The name of the fields
     * to assign when a service matching the corresponding tracked class is
     * added or set to null when the tracked class is removed.
     * If the field is a list,
     * tracked service objects are added and removed instead.</li>
     * </ul>
     * <p>The basic module will register each tracked
     * class type as a notifyEvent template on the
     * ServiceLookup, watching for services that implement
     * that class. When services that match
     * are added or removed from the lookup, the basic
     * module will lookup the class type in
     * the tracker map. For each key in the map that
     * is an instanceof the service the
     * basic module will obtain the value corresponding to
     * that key. If the value is a
     * String array, the following algorithm is carried
     * out for each member of the array,
     * otherwise it is conducted once. Assume in this
     * description that the string value
     * is "fieldName".</p>
     * <ol>
     * <li><b>setter/adder/remover</b> -
     * If a service has been added, search for any method
     * with a signature of (in this order):
     * <ol>
     * <li><code>void addfieldName(Foo)</code></li>
     * <li><code>void addfieldName(Object)</code></li>
     * <li><code>void setfieldName(Object)</code></li>
     * <li><code>void setfieldName(Foo)</code></li>
     * </ol>where (service instanceof Foo). If one
     * exists, use the setter to set the value. If the
     * service is being removed, then search for these methods:
     * <ol>
     * <li><code>void removefieldName(Foo)</code></li>
     * <li><code>void removefieldName(Object)</code></li>
     * <li><code>void setfieldName(Foo)</code></li>
     * <li><code>void setfieldName(Object)</code></li>
     * </ol>
     * if a remove* method is found, the service is
     * passed to the method. If no remove*
     * method is found but a set* method is, then
     * call the set* method with null. If no
     * matchingmethods are found then the basic
     * module will try to set a field directly.</li>
     * <li><b>Field</b> - Search for any field of
     * the class with the same name as the fieldName
     * and of type Foo where (service instanceof Foo)
     * or is a java.util.List. If the field is
     * not a java.util.List, services added will
     * set the field to reference the service, and
     * removal will set the field to null. If the
     * field is of type java.util.List, then
     * added services will be added to the and removal
     * will be remove the service from the
     * list.</li>
     * <li>If either a matching method, or field
     * was found, call the serviceAdded or
     * serviceRemoved method as appropriate with
     * the service. This provides inheriting
     * classes a chance to react to the change (operate in a new
     * mode due to the appearance of a new service.</li>
     * </ol>
     * <p>As you can see, the basic module takes
     * care of all the tedious housekeeping
     * of managing services, and allows inheriting
     * classes to essentially provide a mapping
     * between the names of methods or fields and the
     * types of services you would like to have
     * associate with them. The basic module does it's
     * best to keep these fields up to date.</p>
     * <p/>
     * <p>If you don't want to use a tracker, don't override
     * this method (or return null).</p>
     *
     * @return The classes to track or null to indicate no tracker should be used.
     */
    protected TrackInfo getTrackInfo() {
        return null;
    }

    /**
     * <p>Override this method to receive notification that a service has been added.</p>
     * <p>This method is called after a service has been successfully added and set via
     * a method or field of the class.</p>
     *
     * @param service The service that has been added.
     */
    protected void serviceAdded(Object service) {

    }

    /**
     * <p>Override this method to receive notification
     * that a service has been removed.</p>
     * <p>This method is called after a service has been successfully removed via
     * a method or field (set to null) of the class.</p>
     *
     * @param service The service that has been removed.
     */
    protected void serviceRemoved(Object service) {

    }

    /**
     * <p>Add a service of type track class.</p>
     *
     * @param service    The service to add
     * @param trackClass The class of the service
     * @param field      The name of the method or field that corresponds to this service
     * @return True if the service was successfully added
     */
    private boolean doServiceAdd(Object service, Class trackClass, String field) {
        boolean found = false;
        if (field.length() == 0) {
            found = true;
        }
        else {
            found = tryMethod("add" + field, trackClass, service);
        }
        if (!found) {
            found = tryMethod("add" + field, Object.class, service);
            if (!found) {
                found = tryMethod("set" + field, trackClass, service);
                if (!found) {
                    found = tryMethod("set" + field, Object.class, service);
                    try {
                        Class parent = this.getClass();
                        while (!found && parent != null) {
                            found = matchFields(parent, field, service, true);
                            parent = parent.getSuperclass();
                        }
                    }
                    catch (Exception e) {
                        Log.error("Problem processing service", e);
                    }
                }
            }
        }
        return found;
    }

    /**
     * <p>Tries to find a matching field and adds/removes the service.</p>
     *
     * @param parent  The parent class to search
     * @param field   The field name we're searching for
     * @param service The service to add/remove
     * @param add     True if matching fields result in an add, false to remove the service
     * @return TRue if the service was successfully added
     */
    private boolean matchFields(Class parent, String field, Object service, boolean add) {
        boolean found = false;
        Field[] fields = parent.getFields();
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getName().equals(field)) {
                try {
                    Object fieldObject = fields[i].get(this);
                    if (add) {
                        if (fieldObject instanceof List) {
                            ((List)fieldObject).add(service);
                        }
                        else {
                            fields[i].set(this, service);
                        }
                    }
                    else {
                        if (fieldObject instanceof List) {
                            ((List)fieldObject).remove(service);
                        }
                        else {
                            fields[i].set(this, null);
                        }
                    }
                    found = true;
                    break;
                }
                catch (Exception e) {
                    Log.error("Problem processing service", e);
                }
            }
        }
        return found;
    }

    /**
     * <p>Remove a service of type track class.</p>
     *
     * @param service    The service to remove
     * @param trackClass The class of the service
     * @param field      The name of the method or field that corresponds to this service
     * @return True if the service was successfully removed
     */
    private boolean doServiceRemove(Object service, Class trackClass, String field) {
        boolean found = false;
        if (field.length() == 0) {
            found = true;
        }
        else {
            found = tryMethod("remove" + field, trackClass, service);
        }
        if (!found) {
            found = tryMethod("remove" + field, Object.class, service);
            if (!found) {
                found = tryMethod("set" + field, trackClass, null);
                if (!found) {
                    found = tryMethod("set" + field, Object.class, null);
                    try {
                        Class parent = this.getClass();
                        while (parent != null && !found) {
                            found = matchFields(parent, field, service, false);
                            parent = parent.getSuperclass();
                        }
                    }
                    catch (Exception e) {
                        Log.error("Problem processing service", e);
                    }
                }
            }
        }
        return found;
    }

    /**
     * <p>Try to call a method on this class that
     * takes an object of type trackClass, and call
     * it with the service.</p>
     *
     * @param methodName The name of the method to call
     * @param trackClass The class of the single argument the method should take
     * @param service    The object to call the method with
     * @return True if the method was found and called
     */
    private boolean tryMethod(String methodName, Class trackClass, Object service) {
        boolean found = false;
        try {
            Class parent = this.getClass();
            while (parent != null) {
                Method[] methods = parent.getMethods();
                for (int i = 0; i < methods.length; i++) {
                    Class[] parameters = methods[i].getParameterTypes();
                    if (methods[i].getName().equals(methodName)
                            && parameters.length == 1
                            && parameters[0] == trackClass) {
                        try {
                            methods[i].invoke(this, new Object[]{service});
                            found = true;
                            break;
                        }
                        catch (Exception e) {
                            Log.error("Problem processing service", e);
                        }
                    }
                }
                if (found) {
                    break;
                }
                parent = parent.getSuperclass();
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        ;
        return found;
    }

    /**
     * <p>Listens for tracked services and sets and removes them from the
     * modules.</p>
     *
     * @author Iain Shigeoka
     */
    private class BasicModuleTrackerListener implements ServiceTrackerListener {

        /**
         * <p>Runs through the tracked classes
         * and assigns them according to the algorithm
         * outlined in getTrackInfo.</p>
         *
         * @param service The service being added.
         */
        public void addService(Object service) {
            boolean success = false;
            Iterator tracked = trackInfo.getTrackerClasses().keySet().iterator();
            while (tracked.hasNext()) {
                Class trackClass = (Class)tracked.next();
                if (trackClass.isInstance(service)) {
                    Object field = trackInfo.getTrackerClasses().get(trackClass);
                    if (field instanceof String[]) {
                        String[] fields = (String[])field;
                        for (int i = 0; i < fields.length; i++) {
                            if (doServiceAdd(service, trackClass, fields[i])) {
                                success = true;
                            }
                        }
                    }
                    else {
                        if (doServiceAdd(service, trackClass, (String)field)) {
                            success = true;
                        }
                    }
                }
            }
            if (success) {
                serviceAdded(service);
            }
        }

        /**
         * <p>Runs trough the tracked classes and removes them according to
         * the algorithm outlined in getTrackInfo().</p>
         *
         * @param service The service to remove
         */
        public void removeService(Object service) {
            boolean success = false;
            Iterator tracked = trackInfo.getTrackerClasses().keySet().iterator();
            while (tracked.hasNext()) {
                Class trackClass = (Class)tracked.next();
                if (trackClass.isInstance(service)) {
                    Object field = trackInfo.getTrackerClasses().get(trackClass);
                    if (field instanceof String[]) {
                        String[] fields = (String[])field;
                        for (int i = 0; i < fields.length; i++) {
                            if (doServiceRemove(service, trackClass, fields[i])) {
                                success = true;
                            }
                        }
                    }
                    else {
                        if (doServiceRemove(service, trackClass, (String)field)) {
                            success = true;
                        }
                    }
                }
            }
            if (success) {
                serviceRemoved(service);
            }
        }

    }

    // #####################################################################
    // Basic module management - if you override, make sure to call the
    // parent method as the first thing in the overrriden method.
    // #####################################################################

    /**
     * <p>The 'local' lookup this module should use to locate services
     * on the local server.</p>
     */
    protected ServiceLookup lookup = null;
    /**
     * <p>The registrations the module has registered with the lookup.
     * The BasicModule manages these registrations but inheriting classes
     * may need to manipulate the registrations (such as when attributes for
     * the registration should be changed.</p>
     */
    protected ArrayList registrations = new ArrayList();
    private ServiceTracker tracker = null;
    private TrackInfo trackInfo = null;

    /**
     * <p>Obtain the name of the module.</p>
     *
     * @return The name of the module
     */
    public String getName() {
        return name;
    }

    /**
     * <p>Initializes the basic module.</p>
     * <p/>
     * <p>Inheriting classes that choose to override this method MUST
     * call this initialize() method before accessing BasicModule resources.</p>
     *
     * @param container The container hosting the module
     */
    public void initialize(Container container) {
        try {
            lookup = container.getServiceLookup();
            trackInfo = getTrackInfo();
            if (trackInfo != null) {
                int classCount = trackInfo.getTrackerClasses().keySet().size();
                Class[] trackedClasses =
                        (Class[])trackInfo.getTrackerClasses().keySet().toArray(new Class[classCount]);
                tracker = new ServiceTracker(lookup,
                        new BasicModuleTrackerListener(),
                        trackedClasses);
            }
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    /**
     * <p>Starts the basic module.</p>
     * <p/>
     * <p>Inheriting classes that choose to override this method MUST
     * call this start() method before accessing BasicModule resources.</p>
     *
     * @throws IllegalStateException If start is called before initialize
     *                               successfully returns
     */
    public void start() throws IllegalStateException {
        if (lookup == null) {
            throw new IllegalStateException("Start called before initialize");
        }
        Object[] services = getServices();
        if (services != null) {
            for (int i = 0; i < services.length; i++) {
                if (services[i] != null) {
                    registrations.add(lookup.register(new ServiceItem(null, services[i], null)));
                }
            }
        }
        if (getService() != null) {
            registrations.add(lookup.register(new ServiceItem(null, getService(), null)));
        }
        ServiceItem[] items = getServiceItems();
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) {
                    registrations.add(lookup.register(items[i]));
                }
            }
        }
        if (getServiceItem() != null) {
            registrations.add(lookup.register(getServiceItem()));
        }
    }

    /**
     * <p>Stops the basic module.</p>
     * <p/>
     * <p>Inheriting classes that choose to override this method MUST
     * call this stop() method before accessing BasicModule resources.</p>
     */
    public void stop() {
        Iterator regs = registrations.iterator();
        while (regs.hasNext()) {
            ((ServiceRegistration)regs.next()).cancel();
        }
        registrations.clear();
        if (tracker != null) {
            tracker.cancel();
            tracker = null;
        }
    }

    /**
     * <p>Destroys the module.</p>
     * <p/>
     * <p>Does nothing in the basic module.</p>
     */
    public void destroy() {
    }
}