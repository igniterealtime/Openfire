package org.jivesoftware.openfire.streammanagement;

import java.util.HashMap;
import java.util.Map;

import org.dom4j.Element;
import org.jivesoftware.openfire.Connection;
import org.xmpp.packet.Packet;

/**
 * XEP-0198 Stream Manager.
 * Handles client/server messages acknowledgement.
 *
 * @author jonnyheavey
 */
public class StreamManager {

    /**
     * Stanza namespaces
     */
    public static final String NAMESPACE_V2 = "urn:xmpp:sm:2";
    public static final String NAMESPACE_V3 = "urn:xmpp:sm:3";

	/**
	 * Connection (stream) to client for the session the manager belongs to
	 */
	private Connection connection;

	/**
	 * Whether Stream Management is enabled for session
	 * the manager belongs to.
	 */
	private boolean enabled;

    /**
     * Namespace to be used in stanzas sent to client (depending on XEP-0198 version used by client)
     */
    private String namespace;

    /**
     * Count of how many stanzas/packets
     * have been sent from the server to the client (not necessarily processed)
     */
    private long serverSentStanzas = 0;

    /**
     * Count of how many stanzas/packets
     * sent from the client that the server has processed
     */
    private long serverProcessedStanzas = 0;

    /**
 	 * Count of how many stanzas/packets
     * sent from the server that the client has processed
     */
    private long clientProcessedStanzas = 0;

    /**
     * Collection of stanzas/packets sent to client that haven't been acknowledged.
     */
    private Map<Long, Packet> unacknowledgedServerStanzas = new HashMap<Long, Packet>();

    public StreamManager(Connection connection) {
    	this.setConnection(connection);
    }

    /**
     * Sends XEP-0198 acknowledgement <a /> to client from server
     */
	public void sendServerAcknowledgement() {
		if(isEnabled()) {
			String ack = String.format("<a xmlns='%s' h='%s' />", getNamespace(), getServerProcessedStanzas());
			getConnection().deliverRawText(ack);
		}
	}

	/**
     * Sends XEP-0198 request <r /> to client from server
	 */
	public void sendServerRequest() {
		if(isEnabled()) {
			String request = String.format("<r xmlns='%s' />", getNamespace());
			getConnection().deliverRawText(request);
		}
	}

	/**
	 * Send an error if a XEP-0198 stanza is received at an unexpected time.
	 * e.g. before resource-binding has completed.
	 */
	public void sendUnexpectedError() {
		StringBuilder sb = new StringBuilder(340);
		sb.append(String.format("<failed xmlns='%s'>", getNamespace()));
		sb.append("<unexpected-request xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>");
		sb.append("</failed>");
		getConnection().deliverRawText(sb.toString());
	}

	/**
	 * Receive and process acknowledgement packet from client
	 * @param ack XEP-0198 acknowledgement <a /> stanza to process
	 */
	public void processClientAcknowledgement(Element ack) {
		if(isEnabled()) {
			if(ack.attribute("h") != null) {
				long count = Long.valueOf(ack.attributeValue("h"));
				// Remove stanzas from temporary storage as now acknowledged
				Map<Long,Packet> unacknowledgedStanzas = getUnacknowledgedServerStanzas();
				long i = getClientProcessedStanzas();
				while(i <= count) {
					if(unacknowledgedStanzas.containsKey(i)) {
						unacknowledgedStanzas.remove(i);
					}
					i++;
				}

				setClientProcessedStanzas(count);
			}
		}
	}

	/**
	 * Get connection (stream) for the session
	 * @return
	 */
	public Connection getConnection() {
		return connection;
	}

	/**
	 * Set connection for the session
	 * @param connection
	 */
	public void setConnection(Connection connection) {
		this.connection = connection;
	}

	/**
	 * Determines whether Stream Management enabled for session this
	 * manager belongs to.
	 * @return
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Sets whether Stream Management enabled for session this
	 * manager belongs to.
	 * @param enabled
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;

		if(enabled) {
	    	String enabledStanza = String.format("<enabled xmlns='%s'/>", getNamespace());
	    	getConnection().deliverRawText(enabledStanza);
		}
	}

	/**
	 * Retrieve configured XEP-0198 namespace
	 * @return
	 */
	public String getNamespace() {
		return namespace;
	}

	/**
	 * Configure XEP-0198 namespace
	 * @param namespace
	 */
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	/**
	 * Retrieves number of stanzas sent to client by server.
	 * @return
	 */
	public long getServerSentStanzas() {
		return serverSentStanzas;
	}

	/**
	 * Increments the count of stanzas sent to client by server.
	 */
	public void incrementServerSentStanzas() {
		this.serverSentStanzas++;
	}

	/**
	 * Retrieve the number of stanzas processed by the server since
	 * Stream Management was enabled.
	 * @return
	 */
	public long getServerProcessedStanzas() {
		return serverProcessedStanzas;
	}

	/**
	 * Increments the count of stanzas processed by the server since
	 * Stream Management was enabled.
	 */
	public void incrementServerProcessedStanzas() {
		if(isEnabled()) {
			this.serverProcessedStanzas++;
		}
	}

	/**
	 * Retrieve the number of stanzas processed by the client since
	 * Stream Management was enabled.
	 * @return
	 */
	public long getClientProcessedStanzas() {
		return clientProcessedStanzas;
	}

	/**
	 * Sets the count of stanzas processed by the client since
	 * Stream Management was enabled.
	 */
	public void setClientProcessedStanzas(long count) {
		if(count >= clientProcessedStanzas) {
			clientProcessedStanzas = count;
		}
	}

	/**
	 * Retrieves all unacknowledged stanzas sent to client from server.
	 * @return
	 */
	public Map<Long, Packet> getUnacknowledgedServerStanzas() {
		return unacknowledgedServerStanzas;
	}

}
