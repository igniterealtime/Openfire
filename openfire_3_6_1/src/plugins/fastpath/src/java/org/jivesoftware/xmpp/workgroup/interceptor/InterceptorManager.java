/**
 * $RCSfile$
 * $Revision: 28502 $
 * $Date: 2006-03-13 13:38:47 -0800 (Mon, 13 Mar 2006) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.interceptor;

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.BeanUtils;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class for fastpath packet interceptors.
 *
 * @author Gaston Dombiak
 */
public abstract class InterceptorManager {

    private List<PacketInterceptor> availableInterceptors =
            new CopyOnWriteArrayList<PacketInterceptor>();

    private CopyOnWriteArrayList<PacketInterceptor> globalInterceptors =
            new CopyOnWriteArrayList<PacketInterceptor>();
    private ConcurrentHashMap<String, CopyOnWriteArrayList<PacketInterceptor>> localInterceptors =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<PacketInterceptor>>();

    /**
     * Constructs a new InterceptorManager.
     */
    protected InterceptorManager() {
        loadAvailableInterceptors();
        loadGlobalInterceptors();
    }

    /**
     * Returns an unmodifiable list of global packet interceptors. Global
     * interceptors are applied to all packets read and sent by the server.
     *
     * @return an unmodifiable list of the global packet interceptors.
     */
    public List<PacketInterceptor> getInterceptors() {
        return Collections.unmodifiableList(globalInterceptors);
    }

    /**
     * Returns an unmodifiable list of packet interceptors specific for the specified workgroup.
     *
     * @param workgroup the bare JID address of the workgroup.
     * @return an unmodifiable list of the packet interceptors specific for the specified workgroup.
     */
    public List<PacketInterceptor> getInterceptors(String workgroup) {
        List<PacketInterceptor> interceptors = getLocalInterceptors(workgroup);
        if (interceptors == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(interceptors);
    }

    /**
     * Return the global interceptor at the specified index in the list of currently configured
     * interceptors.
     *
     * @param index the index in the list of interceptors.
     * @return the interceptor at the specified index.
     */
    public PacketInterceptor getInterceptor(int index) {
        if (index < 0 || index > globalInterceptors.size()-1) {
            throw new IllegalArgumentException("Index " + index + " is not valid.");
        }
        return globalInterceptors.get(index);
    }

    /**
     * Return the interceptor at the specified index in the list of currently configured
     * interceptors for the specified workgroup.
     *
     * @param workgroup the bare JID address of the workgroup.
     * @param index the index in the list of interceptors.
     * @return the interceptor at the specified index.
     */
    public PacketInterceptor getInterceptor(String workgroup, int index) {
        List<PacketInterceptor> interceptors = getLocalInterceptors(workgroup);
        if (interceptors == null) {
            return null;
        }
        if (index < 0 || (index > interceptors.size())) {
            throw new IndexOutOfBoundsException("Index " + index + " invalid.");
        }
        return interceptors.get(index);
    }

    /**
     * Inserts a new interceptor at the specified index in the list of global interceptors.
     *
     * @param index the index in the list to insert the new global interceptor at.
     * @param interceptor the interceptor to add.
     */
    public void addInterceptor(int index, PacketInterceptor interceptor) {
        if (interceptor == null) {
            throw new NullPointerException("Parameter interceptor was null.");
        }
        if (index < 0 || (index > globalInterceptors.size())) {
            throw new IndexOutOfBoundsException("Index " + index + " invalid.");
        }
        // Remove the interceptor from the list since the position might have changed
        if (globalInterceptors.contains(interceptor)) {
            int oldIndex = globalInterceptors.indexOf(interceptor);
            if (oldIndex < index) {
                index -= 1;
            }
            globalInterceptors.remove(interceptor);
        }

        globalInterceptors.add(index, interceptor);
        saveInterceptors();
    }

    /**
     * Inserts a new interceptor at specified index in the list of currently configured
     * interceptors for the specified workgroup.
     *
     * @param workgroup the bare JID address of the workgroup.
     * @param index the index in the list to insert the new interceptor at.
     * @param interceptor the interceptor to add.
     */
    public void addInterceptor(String workgroup, int index, PacketInterceptor interceptor) {
        if (interceptor == null) {
            throw new NullPointerException("Parameter interceptor was null.");
        }
        List<PacketInterceptor> interceptors = getLocalInterceptors(workgroup);
        interceptors =
                localInterceptors.putIfAbsent(workgroup,
                        new CopyOnWriteArrayList<PacketInterceptor>());
        if (interceptors == null) {
            interceptors = localInterceptors.get(workgroup);
        }
        if (index < 0 || (index > interceptors.size())) {
            throw new IndexOutOfBoundsException("Index " + index + " invalid.");
        }
        // Remove the interceptor from the list since the position might have changed
        if (interceptors.contains(interceptor)) {
            int oldIndex = interceptors.indexOf(interceptor);
            if (oldIndex < index) {
                index -= 1;
            }
            interceptors.remove(interceptor);
        }

        interceptors.add(index, interceptor);
        saveInterceptors();
    }

    /**
     * Removes the global interceptor from the list.
     *
     * @param interceptor the global interceptor to remove.
     * @return true if the item was present in the list
     */
    public boolean removeInterceptor(PacketInterceptor interceptor) {
        boolean answer = globalInterceptors.remove(interceptor);
        saveInterceptors();
        return answer;
    }

    /**
     * Removes the interceptor for the specified workgroup from the list.
     *
     * @param workgroup the bare JID address of the workgroup.
     * @param interceptor the interceptor to remove.
     * @return true if the item was present in the list
     */
    public boolean removeInterceptor(String workgroup, PacketInterceptor interceptor) {
        List interceptors = getLocalInterceptors(workgroup);
        if (interceptors == null) {
            return false;
        }
        boolean answer = interceptors.remove(interceptor);
        saveInterceptors();
        return answer;
    }

    /**
     * Invokes all currently-installed interceptors on the specified packet.
     * All global interceptors will be invoked as well as interceptors that
     * are related to the specified workgroup.<p>
     *
     * Interceptors are executed before and after processing an incoming packet
     * and sending a packet to a user. This means that interceptors are able to alter or
     * reject packets before they are processed further. If possible, interceptors
     * should perform their work in a short time so that overall performance is not
     * compromised.
     *
     * @param workgroup the bare JID address of the workgroup.
     * @param packet the packet that has been read or is about to be sent.
     * @param read true indicates that the packet was read. When false, the packet
     *      is being sent to a user.
     * @param processed true if the packet has already processed (incoming or outgoing).
     *      If the packet hasn't already been processed, this flag will be false.
     * @throws PacketRejectedException if the packet should be prevented from being processed.
     */
    public void invokeInterceptors(String workgroup, Packet packet, boolean read,
            boolean processed) throws PacketRejectedException
    {
        // Invoke the global interceptors for this packet
        for (PacketInterceptor interceptor : globalInterceptors) {
            try {
                interceptor.interceptPacket(workgroup, packet, read, processed);
            }
            catch (PacketRejectedException e) {
                if (processed) {
                    Log.error("Post interceptor cannot reject packet.", e);
                }
                else {
                    // Throw this exception since we don't really want to catch it
                    throw e;
                }
            }
            catch (Exception e) {
                Log.error("Error in interceptor", e);
            }
        }
        // Invoke the interceptors that are related to the specified workgroup
        List<PacketInterceptor> interceptors = getLocalInterceptors(workgroup);
        if (interceptors != null) {
            for (PacketInterceptor interceptor : interceptors) {
                try {
                    interceptor.interceptPacket(workgroup, packet, read, processed);
                }
                catch (PacketRejectedException e) {
                    if (processed) {
                        Log.error("Post interceptor cannot reject packet.", e);
                    }
                    else {
                        // Throw this exception since we don't really want to catch it
                        throw e;
                    }
                }
                catch (Exception e) {
                    Log.error("Error in interceptor", e);
                }
            }
        }
    }

    public synchronized void saveInterceptors() {
        // Delete all global interceptors stored as properties.
        JiveGlobals.deleteProperty("interceptor.global." + getPropertySuffix());

        // Delete all the workgroup interceptors stored as properties.
        for (String jid : localInterceptors.keySet()) {
            try {
                Workgroup workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(jid));

                Collection<String> propertyNames = workgroup.getProperties().getPropertyNames();
                for (String propertyName : propertyNames) {
                   if (propertyName.startsWith("jive.interceptor." + getPropertySuffix())) {
                       workgroup.getProperties().deleteProperty(propertyName);
                   }
                }
            }
            catch (Exception e) {
               Log.error(e);
           }
        }

        // Save the global interceptors as system properties
        JiveGlobals.setProperties(getPropertiesMap(globalInterceptors,
                "interceptor.global." + getPropertySuffix() + "."));

        // Save all the workgroup interceptors as properties of the workgroups.
        for (String jid : localInterceptors.keySet()) {
            try {
                Workgroup workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(jid));
                Map propertyMap = getPropertiesMap(localInterceptors.get(jid),
                        "jive.interceptor." + getPropertySuffix() + ".");
                for (Iterator i=propertyMap.keySet().iterator(); i.hasNext(); ) {
                    String name = (String)i.next();
                    String value = (String)propertyMap.get(name);
                    workgroup.getProperties().setProperty(name, value);
                }
            }
            catch (Exception e) {
               Log.error(e);
           }
        }
    }

    private Map getPropertiesMap(List<PacketInterceptor> interceptors, String context) {
        // Build the properties map that will be saved later
        Map propertyMap = new HashMap();

        if (!interceptors.isEmpty()) {
            propertyMap.put(context + "interceptorCount", String.valueOf(interceptors.size()));
        }

        // Now write them out again
        for (int i = 0; i < interceptors.size(); i++) {
            PacketInterceptor interceptor = interceptors.get(i);
            String interceptorContext = context + "interceptor" + i + ".";

            // Write out class name
            propertyMap.put(interceptorContext + "className", interceptor.getClass().getName());

            // Write out all properties
            Map interceptorProps = BeanUtils.getProperties(interceptor);
            for (Iterator iter=interceptorProps.keySet().iterator(); iter.hasNext(); ) {
                String name = (String) iter.next();
                String value = (String) interceptorProps.get(name);
                if (value != null && !"".equals(value)) {
                    propertyMap.put(interceptorContext + "properties." + name, value);
                }
            }
        }
        return propertyMap;
    }

    /**
     * Installs a new class into the list of available interceptors for the system. Exceptions
     * are thrown if the class can't be loaded from the classpath, or the class isn't an instance
     * of PacketInterceptor.
     *
     * @param newClass the class to add to the list of available filters in the system.
     * @throws IllegalArgumentException if the class is not a filter or could not be instantiated.
     */
    public synchronized void addInterceptorClass(Class newClass) throws IllegalArgumentException
    {
        try {
            PacketInterceptor newInterceptor = (PacketInterceptor) newClass.newInstance();
            // Make sure the interceptor isn't already in the list.
            for (PacketInterceptor interceptor : availableInterceptors) {
                if (newInterceptor.getClass().equals(interceptor.getClass())) {
                    return;
                }
            }
            // Add in the new interceptor
            availableInterceptors.add(newInterceptor);
            // Write out new class names.
            JiveGlobals.deleteProperty("interceptor.interceptorClasses." + getPropertySuffix());
            for (int i=0; i<availableInterceptors.size(); i++) {
                String cName = availableInterceptors.get(i).getClass().getName();
                JiveGlobals.setProperty("interceptor.interceptorClasses."+ getPropertySuffix() + ".interceptor" + i, cName);
            }
        }
        catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        catch (InstantiationException e2) {
            throw new IllegalArgumentException(e2.getMessage());
        }
        catch (ClassCastException e5) {
            throw new IllegalArgumentException("Class is not a PacketInterceptor");
        }
    }

    /**
     * Returns an array of PacketInterceptor objects that are all of the currently available
     * incerceptors in the system.
     *
     * @return an array of all available interceptors in the current context.
     */
    public List<PacketInterceptor> getAvailableInterceptors() {
        return Collections.unmodifiableList(availableInterceptors);
    }

    /**
     * Returns the suffix to append at the end of global properties to ensure that each subclass
     * is not overwritting the properties of another subclass.
     *
     * @return the suffix to append at the end of global properties to ensure that each subclass
     *         is not overwritting the properties of another subclass.
     */
    protected abstract String getPropertySuffix();

    /**
     * Returns the collection of built-in packet interceptor classes.
     *
     * @return the collection of built-in packet interceptor classes.
     */
    protected abstract Collection<Class> getBuiltInInterceptorClasses();

    private void loadAvailableInterceptors() {
        // Load interceptor classes
        List<PacketInterceptor> interceptorList = new ArrayList<PacketInterceptor>();
        // First, add in built-in list of interceptors.
        for (Class newClass : getBuiltInInterceptorClasses()) {
            try {
                PacketInterceptor interceptor = (PacketInterceptor) newClass.newInstance();
                interceptorList.add(interceptor);
            }
            catch (Exception e) { }
        }

        // Now get custom interceptors.
        List classNames = JiveGlobals.getProperties("interceptor.interceptorClasses." +
                getPropertySuffix());
        for (int i=0; i<classNames.size(); i++) {
            install_interceptor: try {
                Class interceptorClass = loadClass((String)classNames.get(i));
                // Make sure that the interceptor isn't already installed.
                for (int j=0; j<interceptorList.size(); j++) {
                    if (interceptorClass.equals(interceptorList.get(j).getClass())) {
                        break install_interceptor;
                    }
                }
                PacketInterceptor interceptor = (PacketInterceptor) interceptorClass.newInstance();
                interceptorList.add(interceptor);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }

        availableInterceptors.addAll(interceptorList);
    }

    private void loadGlobalInterceptors() {
        // See if a record for this context exists yet. If not, create one.
        int interceptorCount = JiveGlobals.getIntProperty("interceptor.global." +
                getPropertySuffix() +
                ".interceptorCount", 0);

        // Load up all filters and their filter types
        List<PacketInterceptor> interceptorList = new ArrayList<PacketInterceptor>(interceptorCount);
        for (int i=0; i<interceptorCount; i++) {
            try {
                String interceptorContext = "interceptor.global." + getPropertySuffix() + ".interceptor" + i + ".";
                String className = JiveGlobals.getProperty(interceptorContext + "className");
                Class interceptorClass = loadClass(className);
                interceptorList.add((PacketInterceptor) interceptorClass.newInstance());

                // Load properties.
                List props = JiveGlobals.getPropertyNames(interceptorContext + "properties");
                Map interceptorProps = new HashMap();

                for (int k = 0; k < props.size(); k++) {
                    String key = (String)props.get(k);
                    String value = JiveGlobals.getProperty((String)props.get(k));
                    // Get the bean property name, which is everything after the last '.' in the
                    // xml property name.
                    interceptorProps.put(key.substring(key.lastIndexOf(".")+1), value);
                }

                // Set properties on the bean
                BeanUtils.setProperties(interceptorList.get(i), interceptorProps);
            }
            catch (Exception e) {
                Log.error("Error loading global interceptor " + i, e);
            }
        }
        globalInterceptors.addAll(interceptorList);
    }

    private void loadLocalInterceptors(String workgroupJID) throws UserNotFoundException {
        Workgroup workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(workgroupJID));

        int interceptorCount = 0;
        String iCount = workgroup.getProperties().getProperty("jive.interceptor." +
                getPropertySuffix() + ".interceptorCount");
        if (iCount != null) {
            try {
                interceptorCount = Integer.parseInt(iCount);
            }
            catch (NumberFormatException nfe) { /* ignore */ }
        }

        // Load up all intercpetors.
        List<PacketInterceptor> interceptorList = new ArrayList<PacketInterceptor>(interceptorCount);
        for (int i=0; i<interceptorCount; i++) {
            try {
                String interceptorContext = "jive.interceptor." + getPropertySuffix() + ".interceptor" + i + ".";
                String className = workgroup.getProperties().getProperty(interceptorContext +
                        "className");
                Class interceptorClass = loadClass(className);
                interceptorList.add((PacketInterceptor) interceptorClass.newInstance());

                // Load properties.
                Map interceptorProps = new HashMap();
                for (String key : getChildrenPropertyNames(interceptorContext + "properties",
                        workgroup.getProperties().getPropertyNames()))
                {
                    String value = workgroup.getProperties().getProperty(key);
                    // Get the bean property name, which is everything after the last '.' in the
                    // xml property name.
                    interceptorProps.put(key.substring(key.lastIndexOf(".")+1), value);
                }

                // Set properties on the bean
                BeanUtils.setProperties(interceptorList.get(i), interceptorProps);
            }
            catch (Exception e) {
                Log.error("Error loading local interceptor " + i, e);
            }
        }
        localInterceptors.put(workgroupJID,
                new CopyOnWriteArrayList<PacketInterceptor>(interceptorList));
    }

    /**
     * Returns a child property names given a parent and an Iterator of property names.
     *
     * @param parent parent property name.
     * @param properties all property names to search.
     * @return an Iterator of child property names.
     */
    private static Collection<String> getChildrenPropertyNames(String parent, Collection<String> properties) {
        List<String> results = new ArrayList<String>();
        for (String name : properties) {
            if (name.startsWith(parent) && !name.equals(parent)) {
                results.add(name);
            }
        }
        return results;
    }

    private Class loadClass(String className) throws ClassNotFoundException {
        try {
            return ClassUtils.forName(className);
        }
        catch (ClassNotFoundException e) {
            return this.getClass().getClassLoader().loadClass(className);
        }
    }

    private List<PacketInterceptor> getLocalInterceptors(String workgroup) {
        List<PacketInterceptor> interceptors = localInterceptors.get(workgroup);
        if (interceptors == null) {
            if (interceptors == null) {
                synchronized (workgroup.intern()) {
                    try {
                        loadLocalInterceptors(workgroup);
                        interceptors = localInterceptors.get(workgroup);
                    }
                    catch (UserNotFoundException e) {
                        Log.warn(e);
                    }
                }
            }
        }
        return interceptors;
    }
}