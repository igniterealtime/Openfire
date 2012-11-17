/**
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.pep;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.locks.Lock;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.CollectionNode;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PubSubEngine;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

/**
 * Manages the creation, persistence and removal of {@link PEPService}
 * instances.
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * 
 */
public class PEPServiceManager {

	public static final Logger Log = LoggerFactory
			.getLogger(PEPServiceManager.class);

	private final static String GET_PEP_SERVICE = "SELECT DISTINCT serviceID FROM ofPubsubNode WHERE serviceID=?";

	/**
	 * Cache of PEP services. Table, Key: bare JID (String); Value: PEPService
	 */
	private final Cache<String, PEPService> pepServices = CacheFactory
			.createLocalCache("PEPServiceManager");

	private PubSubEngine pubSubEngine = null;

	/**
	 * Retrieves a PEP service -- attempting first from memory, then from the
	 * database.
	 * 
	 * @param jid
	 *            the bare JID of the user that owns the PEP service.
	 * @return the requested PEP service if found or null if not found.
	 */
	public PEPService getPEPService(String jid) {
		PEPService pepService = null;

		final Lock lock = CacheFactory.getLock(jid, pepServices);
		try {
			lock.lock();
			if (pepServices.containsKey(jid)) {
				// lookup in cache
				pepService = pepServices.get(jid);
			} else {
				// lookup in database.
				pepService = loadPEPServiceFromDB(jid);
				
				// always add to the cache, even if it doesn't exist. This will
				// prevent future database lookups.
				pepServices.put(jid, pepService);
			}
		} finally {
			lock.unlock();
		}

		return pepService;
	}

	public PEPService create(JID owner) {
		// Return an error if the packet is from an anonymous, unregistered user
		// or remote user
		if (!XMPPServer.getInstance().isLocal(owner)
				|| !UserManager.getInstance().isRegisteredUser(owner.getNode())) {
			throw new IllegalArgumentException(
					"Request must be initiated by a local, registered user, but is not: "
							+ owner);
		}

		PEPService pepService = null;
		final String bareJID = owner.toBareJID();
		final Lock lock = CacheFactory.getLock(owner, pepServices);
		try {
			lock.lock();

			pepService = pepServices.get(bareJID);
			if (pepService == null) {
				pepService = new PEPService(XMPPServer.getInstance(), bareJID);
				pepServices.put(bareJID, pepService);

				if (Log.isDebugEnabled()) {
					Log.debug("PEPService created for : " + bareJID);
				}
			}
		} finally {
			lock.unlock();
		}

		return pepService;
	}

	/**
	 * Loads a PEP service from the database, if it exists.
	 * 
	 * @param jid
	 *            the JID of the owner of the PEP service.
	 * @return the loaded PEP service, or null if not found.
	 */
	private PEPService loadPEPServiceFromDB(String jid) {
		PEPService pepService = null;

		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			con = DbConnectionManager.getConnection();
			// Get all PEP services
			pstmt = con.prepareStatement(GET_PEP_SERVICE);
			pstmt.setString(1, jid);
			rs = pstmt.executeQuery();
			// Restore old PEPServices
			while (rs.next()) {
				String serviceID = rs.getString(1);

				// Create a new PEPService
				pepService = new PEPService(XMPPServer.getInstance(), serviceID);
				pepServices.put(serviceID, pepService);
				pubSubEngine.start(pepService);

				if (Log.isDebugEnabled()) {
					Log.debug("PEP: Restored service for " + serviceID
							+ " from the database.");
				}
			}
		} catch (SQLException sqle) {
			Log.error(sqle.getMessage(), sqle);
		} finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}

		return pepService;
	}

	/**
	 * Deletes the {@link PEPService} belonging to the specified owner.
	 * 
	 * @param owner
	 *            The JID of the owner of the service to be deleted.
	 */
	public void remove(JID owner) {
		PEPService service = null;

		final Lock lock = CacheFactory.getLock(owner, pepServices);
		try {
			lock.lock();
			service = pepServices.remove(owner.toBareJID());
		} finally {
			lock.unlock();
		}

		if (service == null) {
			return;
		}

		// Delete the user's PEP nodes from memory and the database.
		CollectionNode rootNode = service.getRootCollectionNode();
		for (final Node node : service.getNodes()) {
			if (rootNode.isChildNode(node)) {
				node.delete();
			}
		}
		rootNode.delete();
	}

	public void start(PEPService pepService) {
		pubSubEngine.start(pepService);
	}

	public void start() {
		pubSubEngine = new PubSubEngine(XMPPServer.getInstance()
				.getPacketRouter());
	}

	public void stop() {

		for (PEPService service : pepServices.values()) {
			pubSubEngine.shutdown(service);
		}

		pubSubEngine = null;
	}

	public void process(PEPService service, IQ iq) {
		pubSubEngine.process(service, iq);
	}
	
	public boolean hasCachedService(JID owner) {
		return pepServices.get(owner) != null;
	}
	
	// mimics Shutdown, without killing the timer.
	public void unload(PEPService service) {	
		pubSubEngine.shutdown(service);
	}
}
