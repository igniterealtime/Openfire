/**
 * $RCSfile$
 * $Revision: 3142 $
 * $Date: 2005-12-01 13:39:33 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.interceptor;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Packet;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An InterceptorManager manages the list of global interceptors and per-user
 * interceptors that are invoked before and after packets are read and sent.
 * If an interceptor is installed for a user then it will receive all packets
 * sent or received for <b>any</b> connection of that user.<p>
 *
 * PacketInterceptors that are invoked before the packet is sent or processed
 * (when read) may change the original packet or reject the packet by throwing
 * a {@link PacketRejectedException}. If the interceptor rejects a received packet
 * then the sender of the packet receive a
 * {@link org.xmpp.packet.PacketError.Condition#not_allowed not_allowed} error.
 *
 * @see PacketInterceptor
 * @author Gaston Dombiak
 */
public class InterceptorManager {

    private static InterceptorManager instance = new InterceptorManager();

    private XMPPServer server = XMPPServer.getInstance();
    private List<PacketInterceptor> globalInterceptors =
            new CopyOnWriteArrayList<PacketInterceptor>();
    private Map<String, List<PacketInterceptor>> usersInterceptors =
            new ConcurrentHashMap<String, List<PacketInterceptor>>();

    /**
     * Returns a singleton instance of InterceptorManager.
     *
     * @return an instance of InterceptorManager.
     */
    public static InterceptorManager getInstance() {
        return instance;
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
     * Inserts a new interceptor at the end of the list of currently configured
     * interceptors. This interceptor will be used for all the sent and received packets.
     *
     * @param interceptor the interceptor to add.
     */
    public void addInterceptor(PacketInterceptor interceptor) {
        if (interceptor == null) {
            throw new NullPointerException("Parameter interceptor was null.");
        }
        // Remove the interceptor from the list since the position might have changed
        if (globalInterceptors.contains(interceptor)) {
            globalInterceptors.remove(interceptor);
        }
        globalInterceptors.add(interceptor);
    }

    /**
     * Inserts a new interceptor at specified index in the list of currently configured
     * interceptors. This interceptor will be used for all the sent and received packets.
     *
     * @param index the index in the list to insert the new interceptor at.
     * @param interceptor the interceptor to add.
     */
    public void addInterceptor(int index, PacketInterceptor interceptor) {
        if (index < 0 || (index > globalInterceptors.size())) {
            throw new IndexOutOfBoundsException("Index " + index + " invalid.");
        }
        if (interceptor == null) {
            throw new NullPointerException("Parameter interceptor was null.");
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
    }

    /**
     * Removes the global interceptor from the list.
     *
     * @param interceptor the interceptor to remove.
     * @return true if the item was present in the list
     */
    public boolean removeInterceptor(PacketInterceptor interceptor) {
        return globalInterceptors.remove(interceptor);
    }

    /**
     * Returns an unmodifable list of packet interceptors that are related to the
     * specified username.
     *
     * @param username the name of the user.
     * @return an unmodifiable list of packet interceptors that are related to
     *      the specified username.
     */
    public List<PacketInterceptor> getUserInterceptors(String username) {
        List<PacketInterceptor> userInterceptors = usersInterceptors.get(username);
        if (userInterceptors == null) {
            return Collections.emptyList();
        }
        else {
            return Collections.unmodifiableList(userInterceptors);
        }
    }

    /**
     * Inserts a new interceptor at specified index in the list of currently configured
     * interceptors for a specific username. This interceptor will be used only when a packet
     * was sent or received by the specified username.
     *
     * @param username the name of the user.
     * @param index the index in the list to insert the new interceptor at.
     * @param interceptor the interceptor to add.
     */
    public void addUserInterceptor(String username, int index, PacketInterceptor interceptor) {
        List<PacketInterceptor> userInterceptors = usersInterceptors.get(username);
        if (userInterceptors == null) {
            userInterceptors = new CopyOnWriteArrayList<PacketInterceptor>();
            usersInterceptors.put(username, userInterceptors);
        }
        else {
            if (index < 0 || (index > userInterceptors.size())) {
                throw new IndexOutOfBoundsException("Index " + index + " invalid.");
            }
            if (interceptor == null) {
                throw new NullPointerException("Parameter interceptor was null.");
            }

            // Remove the interceptor from the list since the position might have changed
            if (userInterceptors.contains(interceptor)) {
                int oldIndex = userInterceptors.indexOf(interceptor);
                if (oldIndex < index) {
                    index -= 1;
                }
                userInterceptors.remove(interceptor);
            }
        }
        userInterceptors.add(index, interceptor);
    }

    /**
     * Removes the interceptor from the list of interceptors that are related to a specific
     * username.
     *
     * @param username the name of the user.
     * @param interceptor the interceptor to remove.
     * @return true if the item was present in the list
     */
    public boolean removeUserInterceptor(String username, PacketInterceptor interceptor) {
        boolean answer = false;
        List<PacketInterceptor> userInterceptors = usersInterceptors.get(username);
        if (userInterceptors != null) {
            answer = userInterceptors.remove(interceptor);
            // Remove the entry for this username if the list is now empty
            if (userInterceptors.isEmpty()) {
                usersInterceptors.remove(username);
            }
        }
        return answer;
    }

    /**
     * Invokes all currently-installed interceptors on the specified packet.
     * All global interceptors will be invoked as well as interceptors that
     * are related to the address of the session that received or is sending
     * the packet.<p>
     *
     * Interceptors are executed before and after processing an incoming packet
     * and sending a packet to a user. This means that interceptors are able to alter or
     * reject packets before they are processed further. If possible, interceptors
     * should perform their work in a short time so that overall performance is not
     * compromised.
     *
     * @param packet the packet that has been read or is about to be sent.
     * @param session the session that received the packet or that the packet
     *      will be sent to.
     * @param read true indicates that the packet was read. When false, the packet
     *      is being sent to a user.
     * @param processed true if the packet has already processed (incoming or outgoing).
     *      If the packet hasn't already been processed, this flag will be false.
     * @throws PacketRejectedException if the packet should be prevented from being processed.
     */
    public void invokeInterceptors(Packet packet, Session session, boolean read, boolean processed)
            throws PacketRejectedException
    {
        // Invoke the global interceptors for this packet
        // Checking if collection is empty to prevent creating an iterator of
        // a CopyOnWriteArrayList that is an expensive operation
        if (!globalInterceptors.isEmpty()) {
            for (PacketInterceptor interceptor : globalInterceptors) {
                try {
                    interceptor.interceptPacket(packet, session, read, processed);
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
                catch (Throwable e) {
                    Log.error("Error in interceptor: " + interceptor + " while intercepting: " + packet, e);
                }
            }
        }
        // Invoke the interceptors that are related to the address of the session
        if (usersInterceptors.isEmpty()) {
            // Do nothing
            return;
        }
        String username = session.getAddress().getNode();
        if (username != null && server.isLocal(session.getAddress())) {
            Collection<PacketInterceptor> userInterceptors = usersInterceptors.get(username);
            if (userInterceptors != null && !userInterceptors.isEmpty()) {
                for (PacketInterceptor interceptor : userInterceptors) {
                    try {
                        interceptor.interceptPacket(packet, session, read, processed);
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
                    catch (Throwable e) {
                        Log.error("Error in interceptor: " + interceptor + " while intercepting: " + packet, e);
                    }
                }
            }
        }
    }
}