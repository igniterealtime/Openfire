/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.muc;

import com.google.common.collect.Multimap;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.muc.cluster.ServiceAddedEvent;
import org.jivesoftware.openfire.muc.cluster.ServiceRemovedEvent;
import org.jivesoftware.openfire.muc.cluster.ServiceUpdatedEvent;
import org.jivesoftware.openfire.muc.spi.MUCPersistenceManager;
import org.jivesoftware.openfire.muc.spi.MUCServicePropertyEventDispatcher;
import org.jivesoftware.openfire.muc.spi.MUCServicePropertyEventListener;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.openfire.stats.StatisticsManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.ConsistencyChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Provides centralized management of all configured Multi User Chat (MUC) services.
 * 
 * @author Daniel Henninger
 */
public class MultiUserChatManager extends BasicModule implements MUCServicePropertyEventListener,
        UserEventListener {

    private static final Logger Log = LoggerFactory.getLogger(MultiUserChatManager.class);

    private static final String LOAD_SERVICES = "SELECT subdomain,description,isHidden FROM ofMucService";
    private static final String LOAD_SERVICE = "SELECT description,isHidden FROM ofMucService WHERE subdomain =?";
    private static final String CREATE_SERVICE = "INSERT INTO ofMucService(serviceID,subdomain,description,isHidden) VALUES(?,?,?,?)";
    private static final String UPDATE_SERVICE = "UPDATE ofMucService SET subdomain=?,description=? WHERE serviceID=?";
    private static final String DELETE_SERVICE = "DELETE FROM ofMucService WHERE serviceID=?";
    private static final String LOAD_SERVICE_ID = "SELECT serviceID FROM ofMucService WHERE subdomain=?";
    private static final String LOAD_SUBDOMAIN = "SELECT subdomain FROM ofMucService WHERE serviceID=?";

    /**
     * Statistics keys
     */
    private static final String roomsStatKey = "muc_rooms";
    private static final String occupantsStatKey = "muc_occupants";
    private static final String usersStatKey = "muc_users";
    private static final String incomingStatKey = "muc_incoming";
    private static final String outgoingStatKey = "muc_outgoing";
    private static final String trafficStatGroup = "muc_traffic";

    private final ConcurrentHashMap<String,MultiUserChatService> mucServices = new ConcurrentHashMap<>();

    /**
     * Creates a new MultiUserChatManager instance.
     */
    public MultiUserChatManager() {
        super("Multi user chat manager");
    }

    /**
     * Called when manager starts up, to initialize things.
     */
    @Override
    public void start() {
        super.start();

        loadServices();

        for (MultiUserChatService service : mucServices.values()) {
            registerMultiUserChatService(service, false);
        }

        // Add statistics
        addTotalRoomStats();
        addTotalOccupantsStats();
        addTotalConnectedUsers();
        addNumberIncomingMessages();
        addNumberOutgoingMessages();

        UserEventDispatcher.addListener(this);
        MUCServicePropertyEventDispatcher.addListener(this);
    }

    /**
     * Called when manager is stopped, to clean things up.
     */
    @Override
    public void stop() {
        super.stop();

        UserEventDispatcher.removeListener(this);
        MUCServicePropertyEventDispatcher.removeListener(this);

        // Remove the statistics.
        StatisticsManager.getInstance().removeStatistic(roomsStatKey);
        StatisticsManager.getInstance().removeStatistic(occupantsStatKey);
        StatisticsManager.getInstance().removeStatistic(usersStatKey);
        StatisticsManager.getInstance().removeStatistic(incomingStatKey);
        StatisticsManager.getInstance().removeStatistic(outgoingStatKey);

        for (MultiUserChatService service : mucServices.values()) {
            unregisterMultiUserChatService(service.getServiceName(), false);
        }
    }

    /**
     * Registers a new MultiUserChatService implementation to the manager.
     *
     * This is typically used if you have a custom MUC implementation that you want to register with the manager. In
     * other words, it may not be database stored and may follow special rules, implementing MultiUserChatService.
     * It is also used internally to register services from the database.
     *
     * Triggers the service to start up.
     *
     * An event will be sent to all other cluster nodes to inform them that a new service was added.
     *
     * @param service The MultiUserChatService to be registered.
     * @see #createMultiUserChatService(String, String, boolean)
     */
    public void registerMultiUserChatService(@Nonnull final MultiUserChatService service) {
        registerMultiUserChatService(service, true);
    }

    /**
     * Registers a new MultiUserChatService implementation to the manager.
     *
     * This is typically used if you have a custom MUC implementation that you want to register with the manager. In
     * other words, it may not be database stored and may follow special rules, implementing MultiUserChatService.
     * It is also used internally to register services from the database.
     *
     * Triggers the service to start up.
     *
     * This method has a boolean parameter that controls whether a 'new service added' event is to be sent to all other
     * cluster nodes. This generally is desirable when a new service is being created. A reason to _not_ send such an
     * event is when this method is being invoked as a result of receiving/processing such an event that was received
     * from another cluster node, or when initializing this instance from database content (which will occur on all
     * cluster nodes).
     *
     * @param service The MultiUserChatService to be registered.
     * @param allNodes true if a 'service added' event needs to be sent to other cluster nodes.
     * @see #createMultiUserChatService(String, String, boolean)
     */
    public void registerMultiUserChatService(@Nonnull final MultiUserChatService service, final boolean allNodes) {
        Log.debug("Registering MUC service '{}'", service.getServiceName());
        try {
            ComponentManagerFactory.getComponentManager().addComponent(service.getServiceName(), service);
            mucServices.put(service.getServiceName(), service);
        }
        catch (ComponentException e) {
            Log.error("Unable to register MUC service '{}' as a component.", service.getServiceName(), e);
        }
        if (allNodes) {
            Log.trace("Sending 'service added' event for MUC service '{}' to all other cluster nodes.", service.getServiceName());
            CacheFactory.doClusterTask(new ServiceAddedEvent(service.getServiceName(), service.getDescription(), service.isHidden()));
        }
    }

    /**
     * Unregisters a MultiUserChatService from the manager.
     *
     * It can be used to explicitly unregister services, and is also used internally to unregister database stored services.
     *
     * Triggers the service to shut down.
     *
     * An event will be sent to all other cluster nodes to inform them that a new service was added.
     *
     * @param subdomain The subdomain of the service to be unregistered.
     * @see #removeMultiUserChatService(String)
     */
    public void unregisterMultiUserChatService(@Nonnull final String subdomain) {
        unregisterMultiUserChatService(subdomain, true);
    }

    /**
     * Unregisters a MultiUserChatService from the manager.
     *
     * It can be used to explicitly unregister services, and is also used internally to unregister database stored services.
     *
     * Triggers the service to shut down.
     *
     * This method has a boolean parameter that controls whether a 'service removed' event is to be sent to all other
     * cluster nodes. This generally is desirable when a pre-existing service is being removed. A reason to _not_ send
     * such an event is when this method is being invoked as a result of receiving/processing such an event that was
     * received from another cluster node, or when shutting down this instance (as the service might continue to live on
     * other cluster nodes).
     *
     * @param subdomain The subdomain of the service to be unregistered.
     * @param allNodes true if a 'service removed' event needs to be sent to other cluster nodes.
     * @see #removeMultiUserChatService(String)
     */
    public void unregisterMultiUserChatService(@Nonnull final String subdomain, final boolean allNodes) {
        Log.debug("Unregistering MUC service '{}'", subdomain);
        final MultiUserChatService service = mucServices.remove(subdomain);
        if (service != null) {
            service.shutdown();
            try {
                ComponentManagerFactory.getComponentManager().removeComponent(subdomain);
            }
            catch (ComponentException e) {
                Log.error("Unable to remove MUC service '{}' from component manager.", subdomain, e);
                mucServices.put(subdomain, service);
            }
        }
        if (allNodes) {
            Log.trace("Sending 'service removed' event for MUC service '{}' to all other cluster nodes.", subdomain);
            CacheFactory.doClusterTask(new ServiceRemovedEvent(subdomain));
        }
    }

    /**
     * Returns the number of registered MultiUserChatServices.
     *
     * @param includePrivate True if you want to include private/hidden services in the count.
     * @return Number of registered services.
     */
    public int getServicesCount(final boolean includePrivate) {
        int servicesCnt = mucServices.size();
        if (!includePrivate) {
            for (MultiUserChatService service : mucServices.values()) {
                if (service.isHidden()) {
                    servicesCnt--;
                }
            }
        }
        return servicesCnt;
    }

    /**
     * Creates a new MUC service and registers it with the manager (which causes a cluster-wide notification to be sent)
     * and starts up the service.
     *
     * @param subdomain Subdomain of the MUC service.
     * @param description Description of the MUC service (can be null for default description)
     * @param isHidden True if the service is hidden from view in services lists.
     * @return MultiUserChatService implementation that was just created.
     * @throws AlreadyExistsException if the service already exists.
     */
    @Nonnull
    public MultiUserChatServiceImpl createMultiUserChatService(@Nonnull final String subdomain, @Nullable final String description, final boolean isHidden) throws AlreadyExistsException {
        if (getMultiUserChatServiceID(subdomain) != null) {
            Log.info("Unable to create a service for {} as one already exists.", subdomain);
            throw new AlreadyExistsException();
        }

        Log.info("Creating MUC service '{}'", subdomain);
        final MultiUserChatServiceImpl muc = new MultiUserChatServiceImpl(subdomain, description, isHidden);
        insertService(subdomain, description, isHidden);
        registerMultiUserChatService(muc);
        return muc;
    }

    /**
     * Updates the configuration of a MUC service.
     *
     * This is more involved than it may seem.
     *
     * If the subdomain is changed, we need to shut down the old service and start up the new one, registering
     * the new subdomain and cleaning up the old one.
     *
     * Properties are tied to the ID, which will not change.
     *
     * @param serviceID The ID of the service to be updated.
     * @param subdomain New subdomain to assign to the service.
     * @param description New description to assign to the service.
     * @throws NotFoundException if service was not found.
     */
    public void updateMultiUserChatService(final long serviceID, @Nonnull final String subdomain, @Nullable final String description) throws NotFoundException {
        final MultiUserChatServiceImpl muc = (MultiUserChatServiceImpl) getMultiUserChatService(serviceID);
        if (muc == null) {
            // A NotFoundException is thrown if the specified service was not found.
            Log.info("Unable to find service to update for {}", serviceID);
            throw new NotFoundException();
        }
        Log.info("Updating MUC service '{}'", subdomain);

        final String oldSubdomain = muc.getServiceName();
        if (!mucServices.containsKey(oldSubdomain)) {
            // This should never occur, but just in case...
            throw new NotFoundException();
        }
        if (oldSubdomain.equals(subdomain)) {
            // Alright, all we're changing is the description. This is easy.
            updateService(serviceID, subdomain, description);
            // Update the existing service's description.
            muc.setDescription(description);
            // Broadcast change to other cluster nodes (OF-2164)
            CacheFactory.doSynchronousClusterTask(new ServiceUpdatedEvent(subdomain), false);
        }
        else {
            // Changing the subdomain, here's where it   gets complex.

            // Unregister existing muc service
            unregisterMultiUserChatService(subdomain, false);

            // Update the information stored about the MUC service
            updateService(serviceID, subdomain, description);

            // Create new MUC service with new settings
            final MultiUserChatService replacement = new MultiUserChatServiceImpl(subdomain, description, muc.isHidden());

            // Register to new service
            registerMultiUserChatService(replacement, false);

            // Broadcast change(s) to other cluster nodes (OF-2164)
            CacheFactory.doSynchronousClusterTask(new ServiceAddedEvent(subdomain, description, muc.isHidden()), false);
            CacheFactory.doSynchronousClusterTask(new ServiceRemovedEvent(oldSubdomain), false);
        }
    }

    /**
     * Updates the configuration of a MUC service.
     *
     * This is more involved than it may seem.
     *
     * If the subdomain is changed, we need to shut down the old service and start up the new one, registering the new
     * subdomain and cleaning up the old one.
     *
     * Properties are tied to the ID, which will not change.
     *
     * @param currentSubdomain The current subdomain assigned to the service.
     * @param newSubdomain New subdomain to assign to the service.
     * @param description New description to assign to the service.
     * @throws NotFoundException if service was not found.
     */
    public void updateMultiUserChatService(@Nonnull final String currentSubdomain, @Nonnull final String newSubdomain, @Nullable final String description) throws NotFoundException {
        final Long serviceID = getMultiUserChatServiceID(currentSubdomain);
        if (serviceID == null) {
            Log.info("Unable to find service to update for {}", currentSubdomain);
            throw new NotFoundException();
        }
        updateMultiUserChatService(serviceID, newSubdomain, description);
    }

    /**
     * Deletes a configured MultiUserChatService by subdomain, and shuts it down.
     *
     * @param subdomain The subdomain of the service to be deleted.
     * @throws NotFoundException if the service was not found.
     */
    public void removeMultiUserChatService(@Nonnull final String subdomain) throws NotFoundException {
        final Long serviceID = getMultiUserChatServiceID(subdomain);
        if (serviceID == null) {
            Log.info("Unable to find service to remove for {}", subdomain);
            throw new NotFoundException();
        }
        removeMultiUserChatService(serviceID);
    }

    /**
     * Deletes a configured MultiUserChatService by ID, and shuts it down.
     *
     * @param serviceID The ID opf the service to be deleted.
     * @throws NotFoundException if the service was not found.
     */
    public void removeMultiUserChatService(final long serviceID) throws NotFoundException {
        final MultiUserChatServiceImpl muc = (MultiUserChatServiceImpl) getMultiUserChatService(serviceID);
        if (muc == null) {
            Log.info("Unable to find service to remove for service ID {}", serviceID);
            throw new NotFoundException();
        }
        final String subdomain = muc.getServiceName();
        Log.info("Removing MUC service '{}'", subdomain);
        unregisterMultiUserChatService(subdomain);
        deleteService(serviceID);
    }

    /**
     * Retrieves a MultiUserChatService instance specified by it's service ID.
     *
     * @param serviceID ID of the conference service you wish to query.
     * @return The MultiUserChatService instance associated with the id, or null if none found.
     */
    @Nullable
    public MultiUserChatService getMultiUserChatService(final long serviceID) {
        final String subdomain = getMultiUserChatSubdomain(serviceID);
        if (subdomain == null) {
            return null;
        }
        return mucServices.get(subdomain);
    }

    /**
     * Retrieves a MultiUserChatService instance specified by it's subdomain of the server's primary domain. In other
     * words: if the service is <tt>conference.example.org</tt>, and the server is <tt>example.org</tt>, you would
     * specify <tt>conference</tt> here.
     *
     * @param subdomain Subdomain of the conference service you wish to query.
     * @return The MultiUserChatService instance associated with the subdomain, or null if none found.
     */
    @Nullable
    public MultiUserChatService getMultiUserChatService(@Nonnull final String subdomain) {
        return mucServices.get(subdomain);
    }

    /**
     * Retrieves a MultiUserChatService instance specified by any JID that refers to it. In other words: the argument
     * value can be a XMPP domain name for the service, a room JID, or even the JID of a occupant of the room. The
     * implementation takes the domain part of the JID, strips off the server domain name from the end, leaving only the
     * subdomain, and then calls the subdomain version of the call.
     *
     * @param jid JID that contains a reference to the conference service.
     * @return The MultiUserChatService instance associated with the JID, or null if none found.
     */
    @Nullable
    public MultiUserChatService getMultiUserChatService(@Nonnull final JID jid) {
        final String subdomain = jid.getDomain().replace("."+ XMPPServer.getInstance().getServerInfo().getXMPPDomain(), "");
        return getMultiUserChatService(subdomain);
    }

    /**
     * Retrieves all of the MultiUserChatServices managed and configured for this server, sorted by subdomain.
     *
     * @return A list of MultiUserChatServices configured for this server.
     */
    @Nonnull
    public List<MultiUserChatService> getMultiUserChatServices() {
        final List<MultiUserChatService> services = new ArrayList<>(mucServices.values());
        services.sort(new ServiceComparator());
        return services;
    }

    /**
     * Retrieves the number of MultiUserChatServices that are configured for this server.
     *
     * @return The number of registered MultiUserChatServices.
     */
    public int getMultiUserChatServicesCount() {
        return mucServices.size();
    }

    /**
     * Returns true if a MUC service is configured/exists for a given subdomain.
     *
     * @param subdomain Subdomain of service to check on.
     * @return True or false if the subdomain is registered as a MUC service.
     */
    public boolean isServiceRegistered(@Nullable final String subdomain) {
        if (subdomain == null) {
            return false;
        }
        return mucServices.containsKey(subdomain);
    }

    /**
     * Retrieves the database ID of a MUC service by subdomain.
     *
     * @param subdomain Subdomain of service to get ID of.
     * @return ID number of MUC service, or null if none found.
     */
    public Long getMultiUserChatServiceID(@Nonnull final String subdomain) {
        return loadServiceID(subdomain);
    }

    /**
     * Retrieves the subdomain of a specified service ID.
     *
     * @param serviceID ID of service to get subdomain of.
     * @return Subdomain of MUC service, or null if none found.
     */
    @Nullable
    public String getMultiUserChatSubdomain(final long serviceID) {
        return loadServiceSubdomain(serviceID);
    }

    /**
     * Loads the list of configured services stored in the database.
     *
     * This call will add the services to memory on the local cluster node, but will not propagate them to other nodes
     * in the cluster.
     */
    private void loadServices() {
        Log.debug("Loading all MUC services from the database.");
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_SERVICES);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String subdomain = rs.getString(1);
                String description = rs.getString(2);
                Boolean isHidden = Boolean.valueOf(rs.getString(3));
                final MultiUserChatServiceImpl muc = new MultiUserChatServiceImpl(subdomain, description, isHidden);

                Log.trace("... loaded '{}' MUC service from the database.", subdomain);
                mucServices.put(subdomain, muc);
            }
        }
        catch (Exception e) {
            Log.error("An unexpected exception occurred while trying to load all MUC services from the database.", e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Updates the in-memory representation of a previously loaded services from the database.
     *
     * This call will modify database-stored characteristics for a service previously loaded to memory on the local
     * cluster node. An exception will be thrown if used for a service that's not in memory.
     *
     * Note that this method will not cause MUCServiceProperties to be reloaded. It only operates on fields like the
     * service description.
     *
     * This method is primarily useful to cause a service to reload its state from the database after it was changed on
     * another cluster node.
     *
     * @param subdomain the domain of the service to refresh
     */
    public void refreshService(String subdomain) {
        Log.debug("Refreshing MUC service {} from the database.", subdomain);
        if (!mucServices.containsKey(subdomain)) {
            throw new IllegalArgumentException("Cannot refresh a MUC service that is not loaded: " + subdomain);
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_SERVICE);
            pstmt.setString(1, subdomain);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                String description = rs.getString(1);
                Boolean isHidden = Boolean.valueOf(rs.getString(2));
                ((MultiUserChatServiceImpl)mucServices.get(subdomain)).setDescription(description);
                ((MultiUserChatServiceImpl)mucServices.get(subdomain)).setHidden(isHidden);
            }
            else {
                throw new Exception("Unable to locate database row for subdomain " + subdomain);
            }
        }
        catch (Exception e) {
            Log.error("A database exception occurred while trying to refresh MUC service '{}' from the database.", subdomain, e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Log.trace("Refreshed MUC service '{}'", subdomain);
    }

    /**
     * Gets a specific subdomain/service's ID number.
     *
     * @param subdomain Subdomain to retrieve ID for.
     * @return ID number of service, or null if no such service was found
     */
    @Nullable
    private Long loadServiceID(@Nonnull final String subdomain) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Long id = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_SERVICE_ID);
            pstmt.setString(1, subdomain);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                id = rs.getLong(1);
            }
            else {
                throw new Exception("Unable to locate Service ID for subdomain "+subdomain);
            }
        }
        catch (Exception e) {
            Log.error("A database exception occurred while trying to load the ID for MUC service '{}' from the database.", subdomain, e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Log.trace("Loaded service ID for MUC service '{}'", subdomain);
        return id;
    }

    /**
     * Gets a specific subdomain by a service's ID number.
     *
     * @param serviceID ID to retrieve subdomain for.
     * @return Subdomain of service, or null if no such service was found.
     */
    @Nullable
    private String loadServiceSubdomain(final long serviceID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String subdomain = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_SUBDOMAIN);
            pstmt.setLong(1, serviceID);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                subdomain = rs.getString(1);
            }
        }
        catch (Exception e) {
            Log.error("A database exception occurred while trying to load the subdomain for MUC service with database ID {} from the database.", serviceID, e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Log.trace("Loaded service name for service with ID {}", serviceID);
        return subdomain;
    }

    /**
     * Inserts a new MUC service into the database.
     *
     * @param subdomain Subdomain of new service.
     * @param description Description of MUC service. Can be null for default description.
     * @param isHidden True if the service should be hidden from service listing.
     */
    private void insertService(@Nonnull final String subdomain, @Nullable final String description, final boolean isHidden) {
        Connection con = null;
        PreparedStatement pstmt = null;
        final long serviceID = SequenceManager.nextID(JiveConstants.MUC_SERVICE);
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(CREATE_SERVICE);
            pstmt.setLong(1, serviceID);
            pstmt.setString(2, subdomain);
            if (description != null) {
                pstmt.setString(3, description);
            }
            else {
                pstmt.setNull(3, Types.VARCHAR);
            }
            pstmt.setInt(4, (isHidden ? 1 : 0));
            pstmt.executeUpdate();
            Log.debug("Inserted MUC service '{}' with database ID {}", subdomain, serviceID);
        }
        catch (SQLException e) {
            Log.error("A database exception occurred while trying to insert service '{}' to the database.", subdomain, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Updates an existing service's subdomain and description in the database.
     *
     * @param serviceID ID of the service to update.
     * @param subdomain Subdomain to set service to.
     * @param description Description of MUC service. Can be null for default description.
     */
    private void updateService(final long serviceID, @Nonnull final String subdomain, @Nullable final String description) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_SERVICE);
            pstmt.setString(1, subdomain);
            if (description != null) {
                pstmt.setString(2, description);
            }
            else {
                pstmt.setNull(2, Types.VARCHAR);
            }
            pstmt.setLong(3, serviceID);
            pstmt.executeUpdate();
            Log.debug("Updated MUC service '{}' with database ID {}", subdomain, serviceID);
        }
        catch (SQLException e) {
            Log.error("A database exception occurred while trying to update service with ID {} in the database.", serviceID, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Deletes a service based on service ID.
     *
     * @param serviceID ID of the service to delete.
     */
    private void deleteService(final long serviceID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_SERVICE);
            pstmt.setLong(1, serviceID);
            pstmt.executeUpdate();
            Log.debug("Deleted MUC service with database ID {}", serviceID);
        }
        catch (SQLException e) {
            Log.error("A database exception occurred while trying to remove service with ID {} from the database.", serviceID, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /****************** Statistics code ************************/
    private void addTotalRoomStats() {
        // Register a statistic.
        final Statistic statistic = new Statistic() {
            @Override
            public String getName() {
                return LocaleUtils.getLocalizedString("muc.stats.active_group_chats.name");
            }

            @Override
            public Type getStatType() {
                return Type.count;
            }

            @Override
            public String getDescription() {
                return LocaleUtils.getLocalizedString("muc.stats.active_group_chats.desc");
            }

            @Override
            public String getUnits() {
                return LocaleUtils.getLocalizedString("muc.stats.active_group_chats.units");
            }

            @Override
            public double sample() {
                double rooms = 0;
                for (MultiUserChatService service : getMultiUserChatServices()) {
                    rooms += service.getNumberChatRooms();
                }
                return rooms;
            }

            @Override
            public boolean isPartialSample() {
                return false;
            }
        };
        StatisticsManager.getInstance().addStatistic(roomsStatKey, statistic);
    }

    private void addTotalOccupantsStats() {
        // Register a statistic.
        final Statistic statistic = new Statistic() {
            @Override
            public String getName() {
                return LocaleUtils.getLocalizedString("muc.stats.occupants.name");
            }

            @Override
            public Type getStatType() {
                return Type.count;
            }

            @Override
            public String getDescription() {
                return LocaleUtils.getLocalizedString("muc.stats.occupants.description");
            }

            @Override
            public String getUnits() {
                return LocaleUtils.getLocalizedString("muc.stats.occupants.label");
            }

            @Override
            public double sample() {
                double occupants = 0;
                for (MultiUserChatService service : getMultiUserChatServices()) {
                    occupants += service.getNumberRoomOccupants();
                }
                return occupants;
            }

            @Override
            public boolean isPartialSample() {
                return false;
            }
        };
        StatisticsManager.getInstance().addStatistic(occupantsStatKey, statistic);
    }

    private void addTotalConnectedUsers() {
        // Register a statistic.
        final Statistic statistic = new Statistic() {
            @Override
            public String getName() {
                return LocaleUtils.getLocalizedString("muc.stats.users.name");
            }

            @Override
            public Type getStatType() {
                return Type.count;
            }

            @Override
            public String getDescription() {
                return LocaleUtils.getLocalizedString("muc.stats.users.description");
            }

            @Override
            public String getUnits() {
                return LocaleUtils.getLocalizedString("muc.stats.users.label");
            }

            @Override
            public double sample() {
                double users = 0;
                for (MultiUserChatService service : getMultiUserChatServices()) {
                    users += service.getNumberConnectedUsers();
                }
                return users;
            }

            @Override
            public boolean isPartialSample() {
                return false;
            }
        };
        StatisticsManager.getInstance().addStatistic(usersStatKey, statistic);
    }

    private void addNumberIncomingMessages() {
        // Register a statistic.
        final Statistic statistic = new Statistic() {
            @Override
            public String getName() {
                return LocaleUtils.getLocalizedString("muc.stats.incoming.name");
            }

            @Override
            public Type getStatType() {
                return Type.rate;
            }

            @Override
            public String getDescription() {
                return LocaleUtils.getLocalizedString("muc.stats.incoming.description");
            }

            @Override
            public String getUnits() {
                return LocaleUtils.getLocalizedString("muc.stats.incoming.label");
            }

            @Override
            public double sample() {
                double msgcnt = 0;
                for (MultiUserChatService service : getMultiUserChatServices()) {
                    msgcnt += service.getIncomingMessageCount(true);
                }
                return msgcnt;
            }

            @Override
            public boolean isPartialSample() {
                // Get this value from the other cluster nodes
                return true;
            }
        };
        StatisticsManager.getInstance().addMultiStatistic(incomingStatKey, trafficStatGroup, statistic);
    }

    private void addNumberOutgoingMessages() {
        // Register a statistic.
        final Statistic statistic = new Statistic() {
            @Override
            public String getName() {
                return LocaleUtils.getLocalizedString("muc.stats.outgoing.name");
            }

            @Override
            public Type getStatType() {
                return Type.rate;
            }

            @Override
            public String getDescription() {
                return LocaleUtils.getLocalizedString("muc.stats.outgoing.description");
            }

            @Override
            public String getUnits() {
                return LocaleUtils.getLocalizedString("muc.stats.outgoing.label");
            }

            @Override
            public double sample() {
                double msgcnt = 0;
                for (MultiUserChatService service : getMultiUserChatServices()) {
                    msgcnt += service.getOutgoingMessageCount(true);
                }
                return msgcnt;
            }

            @Override
            public boolean isPartialSample() {
                // Each cluster node knows the total across the cluster
                return false;
            }
        };
        StatisticsManager.getInstance().addMultiStatistic(outgoingStatKey, trafficStatGroup, statistic);
    }

    @Override
    public void propertySet(String service, String property, Map<String, Object> params) {
        // Let everyone know we've had an update.
        CacheFactory.doSynchronousClusterTask(new ServiceUpdatedEvent(service), false);
    }

    @Override
    public void propertyDeleted(String service, String property, Map<String, Object> params) {
        // Let everyone know we've had an update.
        CacheFactory.doSynchronousClusterTask(new ServiceUpdatedEvent(service), false);
    }

    @Override
    public void userCreated(User user, Map<String, Object> params) {
        // Do nothing
    }

    @Override
    public void userDeleting(User user, Map<String, Object> params) {
        // When a user is being deleted, all its affiliations need to be removed from chat rooms (OF-2166). Note that
        // every room is an event listener for the same event, which should update rooms that are loaded into memory
        // from the database. This event handler intends to update rooms that are not in memory, but only in the database.
        MUCPersistenceManager
                .removeAffiliationFromDB(XMPPServer.getInstance().createJID(user.getUsername(), null, true));
    }

    @Override
    public void userModified(User user, Map<String, Object> params) {
        // Do nothing
    }

    private static class ServiceComparator implements Comparator<MultiUserChatService> {
        @Override
        public int compare(MultiUserChatService o1, MultiUserChatService o2) {
            return o1.getServiceName().compareTo(o2.getServiceName());
        }
    }

    /**
     * Verifies that caches and supporting structures around rooms and occupants are in a consistent state.
     *
     * Note that this operation can be costly in terms of resource usage. Use with caution in large / busy systems.
     *
     * The returned multi-map can contain up to four keys: info, fail, pass, data. All entry values are a human readable
     * description of a checked characteristic. When the state is consistent, no 'fail' entries will be returned.
     *
     * @return A consistency state report.
     */
    public List<Multimap<String, String>> clusteringStateConsistencyReportForMucRoomsAndOccupant() {
        return XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServices().stream()
            .map(mucService -> ConsistencyChecks.generateReportForMucRooms(
                mucService.getLocalMUCRoomManager().getROOM_CACHE(),
                mucService.getLocalMUCRoomManager().getLocalRooms(),
                mucService.getOccupantManager().getLocalOccupantsByNode(),
                mucService.getOccupantManager().getNodeByLocalOccupant(),
                mucService.getOccupantManager().getFederatedOccupants(),
                mucService.getServiceName()
            )).collect(Collectors.toList());
    }
}
