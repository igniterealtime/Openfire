/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.muc.cluster.GetNewMemberRoomsRequest;
import org.jivesoftware.openfire.muc.cluster.OccupantAddedEvent;
import org.jivesoftware.openfire.muc.cluster.RoomInfo;
import org.jivesoftware.openfire.muc.cluster.SeniorMemberServicesRequest;
import org.jivesoftware.openfire.muc.cluster.ServiceInfo;
import org.jivesoftware.openfire.muc.cluster.ServiceUpdatedEvent;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.openfire.muc.spi.MUCPersistenceManager;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.JID;

/**
 * Provides centralized management of all configured Multi User Chat (MUC) services.
 * 
 * @author Daniel Henninger
 */
public class MultiUserChatManager extends BasicModule implements ClusterEventListener, MUCServicePropertyEventListener,
        UserEventListener {

    private static final Logger Log = LoggerFactory.getLogger(MultiUserChatManager.class);

    private static final String LOAD_SERVICES = "SELECT subdomain,description,isHidden FROM ofMucService";
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

    private ConcurrentHashMap<String,MultiUserChatService> mucServices = new ConcurrentHashMap<>();

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
            registerMultiUserChatService(service);
        }

        // Add statistics
        addTotalRoomStats();
        addTotalOccupantsStats();
        addTotalConnectedUsers();
        addNumberIncomingMessages();
        addNumberOutgoingMessages();

        ClusterManager.addListener(this);
        UserEventDispatcher.addListener(this);
    }

    /**
     * Called when manager is stopped, to clean things up.
     */
    @Override
    public void stop() {
        super.stop();

        ClusterManager.removeListener(this);
        UserEventDispatcher.removeListener(this);

        // Remove the statistics.
        StatisticsManager.getInstance().removeStatistic(roomsStatKey);
        StatisticsManager.getInstance().removeStatistic(occupantsStatKey);
        StatisticsManager.getInstance().removeStatistic(usersStatKey);
        StatisticsManager.getInstance().removeStatistic(incomingStatKey);
        StatisticsManager.getInstance().removeStatistic(outgoingStatKey);

        for (MultiUserChatService service : mucServices.values()) {
            unregisterMultiUserChatService(service.getServiceName());
        }
    }

    /**
     * Registers a new MultiUserChatService implementation to the manager.
     * This is typically used if you have a custom MUC implementation that you
     * want to register with the manager.  In other words, it may not be database
     * stored and may follow special rules, implementating MultiUserChatService.
     * It is also used internally to register services from the database.  Triggers
     * the service to start up.
     *
     * @param service The MultiUserChatService to be registered.
     */
    public void registerMultiUserChatService(MultiUserChatService service) {
        Log.debug("MultiUserChatManager: Registering MUC service "+service.getServiceName());
        try {
            ComponentManagerFactory.getComponentManager().addComponent(service.getServiceName(), service);
            mucServices.put(service.getServiceName(), service);
        }
        catch (ComponentException e) {
            Log.error("MultiUserChatManager: Unable to add "+service.getServiceName()+" as component.", e);
        }
    }

    /**
     * Unregisters a MultiUserChatService from the manager.  It can be used
     * to explicitly unregister services, and is also used internally to unregister
     * database stored services.  Triggers the service to shut down.
     *
     * @param subdomain The subdomain of the service to be unregistered.
     */
    public void unregisterMultiUserChatService(String subdomain) {
        Log.debug("MultiUserChatManager: Unregistering MUC service "+subdomain);
        MultiUserChatService service = mucServices.get(subdomain);
        if (service != null) {
            service.shutdown();
            try {
                ComponentManagerFactory.getComponentManager().removeComponent(subdomain);
            }
            catch (ComponentException e) {
                Log.error("MultiUserChatManager: Unable to remove "+subdomain+" from component manager.", e);
            }
            mucServices.remove(subdomain);
        }
    }

    /**
     * Returns the number of registered MultiUserChatServices.
     *
     * @param includePrivate True if you want to include private/hidden services in the count.
     * @return Number of registered services.
     */
    public Integer getServicesCount(boolean includePrivate) {
        Integer servicesCnt = mucServices.size();
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
     * Creates a new MUC service and registers it with the manager, and starts up the service.
     *
     * @param subdomain Subdomain of the MUC service.
     * @param description Description of the MUC service (can be null for default description)
     * @param isHidden True if the service is hidden from view in services lists.
     * @return MultiUserChatService implementation that was just created.
     * @throws AlreadyExistsException if the service already exists.
     */
    public MultiUserChatServiceImpl createMultiUserChatService(String subdomain, String description, Boolean isHidden) throws AlreadyExistsException {
        if (getMultiUserChatServiceID(subdomain) != null) throw new AlreadyExistsException();
        MultiUserChatServiceImpl muc = new MultiUserChatServiceImpl(subdomain, description, isHidden);
        insertService(subdomain, description, isHidden);
        registerMultiUserChatService(muc);
        return muc;
    }

    /**
     * Updates the configuration of a MUC service.  This is more involved than it may seem.  If the
     * subdomain is changed, we need to shut down the old service and start up the new one, registering
     * the new subdomain and cleaning up the old one.  Properties are tied to the ID, which will not change.
     *
     * @param serviceID The ID of the service to be updated.
     * @param subdomain New subdomain to assign to the service.
     * @param description New description to assign to the service.
     * @throws NotFoundException if service was not found.
     */
    public void updateMultiUserChatService(Long serviceID, String subdomain, String description) throws NotFoundException {
        MultiUserChatServiceImpl muc = (MultiUserChatServiceImpl) getMultiUserChatService(serviceID);
        if (muc == null) throw new NotFoundException();
        // A NotFoundException is thrown if the specified service was not found.
        String oldsubdomain = muc.getServiceName();
        if (!mucServices.containsKey(oldsubdomain)) {
            // This should never occur, but just in case...
            throw new NotFoundException();
        }
        if (oldsubdomain.equals(subdomain)) {
            // Alright, all we're changing is the description.  This is easy.
            updateService(serviceID, subdomain, description);
            // Update the existing service's description.
            muc.setDescription(description);
        }
        else {
            // Changing the subdomain, here's where it gets complex.
            // Unregister existing muc service
            unregisterMultiUserChatService(subdomain);
            // Update the information stored about the MUC service
            updateService(serviceID, subdomain, description);
            // Create new MUC service with new settings
            muc = new MultiUserChatServiceImpl(subdomain, description, muc.isHidden());
            // Register to new service
            registerMultiUserChatService(muc);
        }
    }

    /**
     * Updates the configuration of a MUC service.  This is more involved than it may seem.  If the
     * subdomain is changed, we need to shut down the old service and start up the new one, registering
     * the new subdomain and cleaning up the old one.  Properties are tied to the ID, which will not change.
     *
     * @param cursubdomain The current subdomain assigned to the service.
     * @param newsubdomain New subdomain to assign to the service.
     * @param description New description to assign to the service.
     * @throws NotFoundException if service was not found.
     */
    public void updateMultiUserChatService(String cursubdomain, String newsubdomain, String description) throws NotFoundException {
        Long serviceID = getMultiUserChatServiceID(cursubdomain);
        if (serviceID == null) throw new NotFoundException();
        updateMultiUserChatService(serviceID, newsubdomain, description);
    }

    /**
     * Deletes a configured MultiUserChatService by subdomain, and shuts it down.
     *
     * @param subdomain The subdomain of the service to be deleted.
     * @throws NotFoundException if the service was not found.
     */
    public void removeMultiUserChatService(String subdomain) throws NotFoundException {
        Long serviceID = getMultiUserChatServiceID(subdomain);
        if (serviceID == null) {
            Log.error("MultiUserChatManager: Unable to find service to remove for "+subdomain);
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
    public void removeMultiUserChatService(Long serviceID) throws NotFoundException {
        MultiUserChatServiceImpl muc = (MultiUserChatServiceImpl) getMultiUserChatService(serviceID);
        if (muc == null) {
            Log.error("MultiUserChatManager: Unable to find service to remove for service ID "+serviceID);
            throw new NotFoundException();
        }
        unregisterMultiUserChatService(muc.getServiceName());
        deleteService(serviceID);
    }

    /**
     * Retrieves a MultiUserChatService instance specified by it's service ID.
     *
     * @param serviceID ID of the conference service you wish to query.
     * @return The MultiUserChatService instance associated with the id, or null if none found.
     */
    public MultiUserChatService getMultiUserChatService(Long serviceID) {
        String subdomain = getMultiUserChatSubdomain(serviceID);
        if (subdomain == null) return null;
        return mucServices.get(subdomain);
    }


    /**
     * Retrieves a MultiUserChatService instance specified by it's subdomain of the
     * server's primary domain.  In other words, if the service is conference.example.org,
     * and the server is example.org, you would specify conference here.
     *
     * @param subdomain Subdomain of the conference service you wish to query.
     * @return The MultiUserChatService instance associated with the subdomain, or null if none found.
     */
    public MultiUserChatService getMultiUserChatService(String subdomain) {
        return mucServices.get(subdomain);
    }

    /**
     * Retrieves a MultiUserChatService instance specified by any JID that refers to it.
     * In other words, it can be a hostname for the service, a room JID, or even the JID
     * of a occupant of the room.  Basically it takes the hostname part of the JID,
     * strips off the server hostname from the end, leaving only the subdomain, and then calls
     * the subdomain version of the call.
     *
     * @param jid JID that contains a reference to the conference service.
     * @return The MultiUserChatService instance associated with the JID, or null if none found.
     */
    public MultiUserChatService getMultiUserChatService(JID jid) {
        String subdomain = jid.getDomain().replace("."+ XMPPServer.getInstance().getServerInfo().getXMPPDomain(), "");
        return getMultiUserChatService(subdomain);
    }

    /**
     * Retrieves all of the MultiUserChatServices managed and configured for this server, sorted by
     * subdomain.
     *
     * @return A list of MultiUserChatServices configured for this server.
     */
    public List<MultiUserChatService> getMultiUserChatServices() {
        List<MultiUserChatService> services = new ArrayList<>(mucServices.values());
        Collections.sort(services, new ServiceComparator());
        return services;
    }

    /**
     * Retrieves the number of MultiUserChatServices that are configured for this server.
     *
     * @return The number of registered MultiUserChatServices.
     */
    public Integer getMultiUserChatServicesCount() {
        return mucServices.size();
    }

    /**
     * Returns true if a MUC service is configured/exists for a given subdomain.
     *
     * @param subdomain Subdomain of service to check on.
     * @return True or false if the subdomain is registered as a MUC service.
     */
    public boolean isServiceRegistered(String subdomain) {
        if (subdomain == null) return false;
        return mucServices.containsKey(subdomain);
    }

    /**
     * Retrieves ID of MUC service by subdomain.
     *
     * @param subdomain Subdomain of service to get ID of.
     * @return ID number of MUC service, or null if none found.
     */
    public Long getMultiUserChatServiceID(String subdomain) {
        Long id = loadServiceID(subdomain);
        if (id == -1) {
            return null;
        }
        return id;
    }

    /**
     * Retrieves the subdomain of a specified service ID.
     *
     * @param serviceID ID of service to get subdomain of.
     * @return Subdomain of MUC service, or null if none found.
     */
    public String getMultiUserChatSubdomain(Long serviceID) {
        return loadServiceSubdomain(serviceID);
    }

    /**
     * Loads the list of configured services stored in the database.
     */
    private void loadServices() {
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
                MultiUserChatServiceImpl muc = new MultiUserChatServiceImpl(subdomain, description, isHidden);
                mucServices.put(subdomain, muc);
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Gets a specific subdomain/service's ID number.
     * @param subdomain Subdomain to retrieve ID for.
     * @return ID number of service.
     */
    private long loadServiceID(String subdomain) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Long id = (long)-1;
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
            // No problem, considering this as a "not found".
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return id;
    }

    /**
     * Gets a specific subdomain by a service's ID number.
     * @param serviceID ID to retrieve subdomain for.
     * @return Subdomain of service.
     */
    private String loadServiceSubdomain(Long serviceID) {
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
            else {
                throw new Exception("Unable to locate subdomain for service ID "+serviceID);
            }
        }
        catch (Exception e) {
            // No problem, considering this as a "not found".
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return subdomain;
    }

    /**
     * Inserts a new MUC service into the database.
     * @param subdomain Subdomain of new service.
     * @param description Description of MUC service.  Can be null for default description.
     * @param isHidden True if the service should be hidden from service listing.
     */
    private void insertService(String subdomain, String description, Boolean isHidden) {
        Connection con = null;
        PreparedStatement pstmt = null;
        Long serviceID = SequenceManager.nextID(JiveConstants.MUC_SERVICE);
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
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Updates an existing service's subdomain and description in the database.
     * @param serviceID ID of the service to update.
     * @param subdomain Subdomain to set service to.
     * @param description Description of MUC service.  Can be null for default description.
     */
    private void updateService(Long serviceID, String subdomain, String description) {
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
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Deletes a service based on service ID.
     * @param serviceID ID of the service to delete.
     */
    private void deleteService(Long serviceID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_SERVICE);
            pstmt.setLong(1, serviceID);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /****************** Statistics code ************************/
    private void addTotalRoomStats() {
        // Register a statistic.
        Statistic statistic = new Statistic() {
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
        Statistic statistic = new Statistic() {
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
        Statistic statistic = new Statistic() {
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
                    users += service.getNumberConnectedUsers(false);
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
        Statistic statistic = new Statistic() {
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
        Statistic statistic = new Statistic() {
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

    // Cluster management tasks
    @Override
    public void joinedCluster() {
        if (!ClusterManager.isSeniorClusterMember()) {
            // Get transient rooms and persistent rooms with occupants from senior
            // cluster member and merge with local ones. If room configuration was
            // changed in both places then latest configuration will be kept
            @SuppressWarnings("unchecked")
            List<ServiceInfo> result = (List<ServiceInfo>) CacheFactory.doSynchronousClusterTask(
                    new SeniorMemberServicesRequest(), ClusterManager.getSeniorClusterMember().toByteArray());
            if (result != null) {
                for (ServiceInfo serviceInfo : result) {
                    MultiUserChatService service;
                    service = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceInfo.getSubdomain());
                    if (service == null) {
                        // This is a service we don't know about yet, create it locally and register it;
                        service = new MultiUserChatServiceImpl(serviceInfo.getSubdomain(), serviceInfo.getDescription(), serviceInfo.isHidden());
                        XMPPServer.getInstance().getMultiUserChatManager().registerMultiUserChatService(service);
                    }

                    MultiUserChatServiceImpl serviceImpl = (MultiUserChatServiceImpl)service;

                    for (RoomInfo roomInfo : serviceInfo.getRooms()) {
                        LocalMUCRoom remoteRoom = roomInfo.getRoom();
                        LocalMUCRoom localRoom = serviceImpl.getLocalChatRoom(remoteRoom.getName());
                        if (localRoom == null) {
                            // Create local room with remote information
                            localRoom = remoteRoom;
                            serviceImpl.chatRoomAdded(localRoom);
                        }
                        else {
                            // Update local room with remote information
                            localRoom.updateConfiguration(remoteRoom);
                        }
                        // Add remote occupants to local room
                        // TODO Handle conflict of nicknames
                        for (OccupantAddedEvent event : roomInfo.getOccupants()) {
                            event.setSendPresence(true);
                            event.run();
                        }
                    }
                }
            }
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void joinedCluster(byte[] nodeID) {
        Object result = CacheFactory.doSynchronousClusterTask(new GetNewMemberRoomsRequest(), nodeID);
        if (result instanceof List<?>) {
            List<RoomInfo> rooms = (List<RoomInfo>) result;
            for (RoomInfo roomInfo : rooms) {
                LocalMUCRoom remoteRoom = roomInfo.getRoom();
                MultiUserChatServiceImpl service = (MultiUserChatServiceImpl)remoteRoom.getMUCService();
                LocalMUCRoom localRoom = service.getLocalChatRoom(remoteRoom.getName());
                if (localRoom == null) {
                    // Create local room with remote information
                    localRoom = remoteRoom;
                    service.chatRoomAdded(localRoom);
                }
                // Add remote occupants to local room
                for (OccupantAddedEvent event : roomInfo.getOccupants()) {
                    event.setSendPresence(true);
                    event.run();
                }
            }
        }
    }

    @Override
    public void leftCluster() {
        // Do nothing. An unavailable presence will be created for occupants hosted in other cluster nodes.
    }

    @Override
    public void leftCluster(byte[] nodeID) {
        // Remove all room occupants linked to the defunct node as their sessions are cleaned out earlier
        Log.debug("Removing orphaned occupants associated with defunct node: " +  new String(nodeID, StandardCharsets.UTF_8));

        for (MultiUserChatService service : getMultiUserChatServices()) {
            for (MUCRoom mucRoom : service.getChatRooms()) {
                for (MUCRole mucRole : mucRoom.getOccupants()) {
                    if (mucRole.getNodeID().equals(nodeID)) {
                        mucRoom.leaveRoom(mucRole);
                    }
                }
            }
        }
    }

    @Override
    public void markedAsSeniorClusterMember() {
        // Do nothing
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
        // Delete any affiliation of the user to any room of any MUC service
        MUCPersistenceManager
                .removeAffiliationFromDB(XMPPServer.getInstance().createJID(user.getUsername(), null, true));
        // TODO Delete any user information from the rooms loaded into memory
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

}
