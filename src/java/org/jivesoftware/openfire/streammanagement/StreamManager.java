package org.jivesoftware.openfire.streammanagement;

import java.net.UnknownHostException;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;

import org.dom4j.Element;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

/**
 * XEP-0198 Stream Manager.
 * Handles client/server messages acknowledgement.
 *
 * @author jonnyheavey
 */
public class StreamManager {
	private final Logger Log;
    public static class UnackedPacket {
        public final Date timestamp;
        public final Packet packet;
        
        public UnackedPacket(Date date, Packet p) {
            timestamp = date;
            packet = p;
        }
    }

    /**
     * Stanza namespaces
     */
    public static final String NAMESPACE_V2 = "urn:xmpp:sm:2";
    public static final String NAMESPACE_V3 = "urn:xmpp:sm:3";

	/**
	 * Connection (stream) to client for the session the manager belongs to
	 */
	private final Connection connection;

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
    
    static private long mask = 0xFFFFFFFF; /* 2**32 - 1; this is used to emulate rollover */

    /**
     * Collection of stanzas/packets sent to client that haven't been acknowledged.
     */
    private Deque<UnackedPacket> unacknowledgedServerStanzas = new LinkedList<>();

    public StreamManager(Connection connection) {
		String address;
		try {
			address = connection.getHostAddress();
		}
		catch ( UnknownHostException e )
		{
			address = null;
		}

		this.Log = LoggerFactory.getLogger(StreamManager.class + "["+ (address == null ? "(unknown address)" : address) +"]" );
    	this.connection = connection;
    }

    /**
     * Sends XEP-0198 acknowledgement <a /> to client from server
     */
	public void sendServerAcknowledgement() {
		if(isEnabled()) {
			String ack = String.format("<a xmlns='%s' h='%s' />", getNamespace(), getServerProcessedStanzas() & mask);
			getConnection().deliverRawText(ack);
		}
	}

	/**
         * Sends XEP-0198 request <r /> to client from server
	 */
	private void sendServerRequest() {
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
		getConnection().deliverRawText(
				String.format( "<failed xmlns='%s'>", getNamespace() )
				+ new PacketError( PacketError.Condition.unexpected_request ).toXML()
				+ "</failed>"
		);
	}

	/**
	 * Receive and process acknowledgement packet from client
	 * @param ack XEP-0198 acknowledgement <a /> stanza to process
	 */
	public void processClientAcknowledgement(Element ack) {
		if(isEnabled()) {
			synchronized (this) {
				if (ack.attribute("h") != null) {
					long count = Long.valueOf(ack.attributeValue("h"));
					// Remove stanzas from temporary storage as now acknowledged
					Log.debug("Ack: h={} mine={} length={}", count, clientProcessedStanzas, unacknowledgedServerStanzas.size());
					if (count < clientProcessedStanzas) {
                                    /* Consider rollover? */
						Log.debug("Maybe rollover");
						if (clientProcessedStanzas > mask) {
							while (count < clientProcessedStanzas) {
								Log.debug("Rolling...");
								count += mask + 1;
							}
						}
					}
					while (clientProcessedStanzas < count) {
						unacknowledgedServerStanzas.removeFirst();
						clientProcessedStanzas++;
						Log.debug("In Ack: h={} mine={} length={}", count, clientProcessedStanzas, unacknowledgedServerStanzas.size());
					}

					if(count >= clientProcessedStanzas) {
						clientProcessedStanzas = count;
					}
				}
			}
		}
	}

	public void sentStanza(Packet packet) {

		if(isEnabled()) {
			synchronized (this) {
				incrementServerSentStanzas();
				// Temporarily store packet until delivery confirmed
				unacknowledgedServerStanzas.addLast( new StreamManager.UnackedPacket( new Date(), packet.createCopy() ) );
				Log.debug("Added stanza of type {}, now {} / {}", packet.getClass().getName(), getServerSentStanzas(), unacknowledgedServerStanzas.size());
			}
			if(getServerSentStanzas() % JiveGlobals.getLongProperty("stream.management.requestFrequency", 5) == 0) {
				sendServerRequest();
			}
		}

	}

	public void onClose(PacketRouter router, JID serverAddress) {
		// Re-deliver unacknowledged stanzas from broken stream (XEP-0198)
		if(isEnabled()) {
			setEnabled(false); // Avoid concurrent usage.
			synchronized (this) {
				if (!unacknowledgedServerStanzas.isEmpty()) {
					for (StreamManager.UnackedPacket unacked : unacknowledgedServerStanzas) {
						if (unacked.packet instanceof Message) {
							Message m = (Message) unacked.packet;
							if (m.getExtension("delay", "urn:xmpp:delay") == null) {
								Element delayInformation = m.addChildElement("delay", "urn:xmpp:delay");
								delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.timestamp));
								delayInformation.addAttribute("from", serverAddress.toBareJID());
							}
						}
						router.route(unacked.packet);
					}
				}
			}
		}

	}

	/**
	 * Get connection (stream) for the session
	 * @return connection (stream) for the session
	 */
	public Connection getConnection() {
		return connection;
	}

	/**
	 * Determines whether Stream Management enabled for session this
	 * manager belongs to.
	 * @return true when stream management is enabled, otherwise false.
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Sets whether Stream Management enabled for session this
	 * manager belongs to.
	 * @param enabled true when stream management is to be enabled, false when it is to be disabled.
	 */
	synchronized public void setEnabled(boolean enabled) {
		this.enabled = enabled;

		if(enabled) {
	    	String enabledStanza = String.format("<enabled xmlns='%s'/>", getNamespace());
	    	getConnection().deliverRawText(enabledStanza);
		}
	}

	/**
	 * Retrieve configured XEP-0198 namespace (depending on XEP-0198 version used by client)
	 * @return XEP-0198 namespace
	 */
	public String getNamespace() {
		return namespace;
	}

	/**
	 * Configure XEP-0198 namespace (depending on XEP-0198 version used by client).
	 * @param namespace sets XEP-0198 namespace
	 */
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	/**
	 * Retrieves number of stanzas sent to client by server.
	 * @return number of stanzas sent to client by server.
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
	 * @return the number of stanzas processed by the server
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
}
