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

package org.jivesoftware.messenger.interceptor;

import org.jivesoftware.messenger.Session;
import org.xmpp.packet.Packet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An InterceptorManager holds the list of global interceptors and interceptors per-user that will
 * be invoked before and after a packet was read in SocketReadThead and also when the packet is
 * about to be sent in SocketConnection. If the interceptor is related to a user then it will
 * get all the packets sent or received for <b>any</b> connection of the user.<p>
 *
 * PacketInterceptors that are invoked before the packet is sent or processed (when read) may
 * change the original packet or may even reject the packet by throwing an exception. If the
 * interceptor rejects a received packet then the sender of the packet will get a not_allowed
 * answer.<p>
 *
 * @see PacketInterceptor
 * @author Gaston Dombiak
 */
public class InterceptorManager {

    private static InterceptorManager instance = new InterceptorManager();

    private List<PacketInterceptor> globalInterceptors = new CopyOnWriteArrayList<PacketInterceptor>();
    private Map<String, List<PacketInterceptor>> usersInterceptors = new ConcurrentHashMap<String, List<PacketInterceptor>>();

    public static InterceptorManager getInstance() {
        return instance;
    }

    /**
     * Returns the list of global packet interceptors. These are the interceptors that will be
     * used for all the read and sent packets.
     *
     * @return the list of global packet interceptors.
     */
    public Collection<PacketInterceptor> getInterceptors() {
        return Collections.unmodifiableCollection(globalInterceptors);
    }

    /**
     * Inserts a new interceptor at the end of the list of currently configured
     * interceptors. This interceptor will be used for all the sent and received packets.
     *
     * @param interceptor the interceptor to add.
     */
    public void addInterceptor(PacketInterceptor interceptor) {
        addInterceptor(globalInterceptors.size(), interceptor);
    }

    /**
     * Inserts a new interceptor at specified index in the list of currently configured
     * interceptors. This interceptor will be used for all the sent and received packets.
     *
     * @param index       the index in the list to insert the new interceptor at.
     * @param interceptor the interceptor to add.
     */
    public void addInterceptor(int index, PacketInterceptor interceptor) {
        // Remove the interceptor from the list since the position might have changed
        if (globalInterceptors.contains(interceptor)) {
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
     * Returns the list of packet interceptors that are related to the specified username. These
     * are the interceptors that will be used only when a packet was sent or received by the
     * specified username.
     *
     * @param username the name of the user.
     * @return the list of packet interceptors that are related to the specified username.
     */
    public Collection<PacketInterceptor> getUserInterceptors(String username) {
        List<PacketInterceptor> userInterceptors = usersInterceptors.get(username);
        if (userInterceptors == null) {
            return new ArrayList<PacketInterceptor>();
        }
        else {
            return Collections.unmodifiableCollection(userInterceptors);
        }
    }

    /**
     * Inserts a new interceptor at specified index in the list of currently configured
     * interceptors for a specific username. This interceptor will be used only when a packet
     * was sent or received by the specified username.
     *
     * @param username    the name of the user.
     * @param index       the index in the list to insert the new interceptor at.
     * @param interceptor the interceptor to add.
     */
    public void addUserInterceptor(String username, int index, PacketInterceptor interceptor) {
        List<PacketInterceptor> userInterceptors = usersInterceptors.get(username);
        if (userInterceptors == null) {
            userInterceptors = new CopyOnWriteArrayList<PacketInterceptor>();
            usersInterceptors.put(username, userInterceptors);
        }
        else {
            // Remove the interceptor from the list since the position might have changed
            if (userInterceptors.contains(interceptor)) {
                userInterceptors.remove(interceptor);
            }
        }
        userInterceptors.add(index, interceptor);
    }

    /**
     * Removes the interceptor from the list of interceptors that are related to a specific
     * username.
     *
     * @param username    the name of the user.
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
     * Invokes all currently-installed interceptors on the specified packet. All global
     * interceptors will be invoked as well as interceptors that are related to the address of the
     * session that received or is sending the packet.<p>
     *
     * Interceptors may be executed before processing a read packet or sending a packet to a user.
     * This means that interceptors are able to alter the read or packet to send. If possible
     * interceptors should perform their work in a short time so the overall performance is not
     * compromised.
     *
     * @param packet    the packet that has been read or sent.
     * @param session   the session that received the packet or the packet was sent to.
     * @param read      flag that indicates if the packet was read or sent.
     * @param processed flag that indicates if the action (read/send) was performed. (PRE vs. POST).
     * @throws PacketRejectedException if the packet should be prevented from being processed.
     */
    public void invokeInterceptors(Packet packet, Session session, boolean read, boolean processed)
            throws PacketRejectedException {
        // Invoke the global interceptors for this packet
        for (PacketInterceptor interceptor : globalInterceptors) {
            interceptor.interceptPacket(packet, session, read, processed);
        }
        // Invoke the interceptors that are related to the address of the session
        String username = session.getAddress().getNode();
        if (username != null) {
            Collection<PacketInterceptor> userInterceptors = usersInterceptors.get(username);
            if (userInterceptors != null && !userInterceptors.isEmpty()) {
                for (PacketInterceptor interceptor : userInterceptors) {
                    interceptor.interceptPacket(packet, session, read, processed);
                }
            }
        }
    }
}
