package org.jivesoftware.openfire.streammanagement;

import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.*;

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
		public final long x;
        public final Date timestamp = new Date();
        public final Packet packet;
        
        public UnackedPacket(long x, Packet p) {
			this.x = x;
            packet = p;
        }
    }
    
    public static final String SM_ACTIVE = "stream.management.active";

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
     * Namespace to be used in stanzas sent to client (depending on XEP-0198 version used by client)
     */
    private String namespace;

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

    static private long mask = new BigInteger("2").pow(32).longValue() - 1; // This is used to emulate rollover.

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
	 * Processes a stream management element.
	 *
	 * @param element The stream management element to be processed.
	 * @param onBehalfOf The (full) JID of the entity for which the element is processed.
	 */
	public void process( Element element, JID onBehalfOf )
	{
		switch(element.getName()) {
			case "enable":
				enable( onBehalfOf, element.getNamespace().getStringValue() );
				break;
			case "r":
				sendServerAcknowledgement();
				break;
			case "a":
				processClientAcknowledgement( element);
				break;
			default:
				sendUnexpectedError();
		}
	}

	/**
	 * Attempts to enable Stream Management for the entity identified by the provided JID.
	 *
	 * @param onBehalfOf The address of the entity for which SM is to be enabled.
	 * @param namespace The namespace that defines what version of SM is to be enabled.
	 */
	private void enable( JID onBehalfOf, String namespace )
	{
		// Ensure that resource binding has occurred.
		if( onBehalfOf.getResource() == null ) {
			sendUnexpectedError();
			return;
		}

		synchronized ( this )
		{
			// Do nothing if already enabled
			if ( isEnabled() )
			{
				return;
			}

			this.namespace = namespace;
		}

		// Send confirmation to the requestee.
		connection.deliverRawText( String.format( "<enabled xmlns='%s'/>", namespace ) );
	}

	/**
     * Sends XEP-0198 acknowledgement &lt;a /&gt; to client from server
     */
	public void sendServerAcknowledgement() {
		if(isEnabled()) {
			String ack = String.format("<a xmlns='%s' h='%s' />", namespace, serverProcessedStanzas & mask);
			connection.deliverRawText( ack );
		}
	}

	/**
	 * Sends XEP-0198 request <r /> to client from server
	 */
	private void sendServerRequest() {
		if(isEnabled()) {
			String request = String.format("<r xmlns='%s' />", namespace);
			connection.deliverRawText( request );
		}
	}

	/**
	 * Send an error if a XEP-0198 stanza is received at an unexpected time.
	 * e.g. before resource-binding has completed.
	 */
	private void sendUnexpectedError() {
		connection.deliverRawText(
				String.format( "<failed xmlns='%s'>", namespace )
						+ new PacketError( PacketError.Condition.unexpected_request ).toXML()
						+ "</failed>"
		);
	}

	/**
	 * Receive and process acknowledgement packet from client
	 * @param ack XEP-0198 acknowledgement <a /> stanza to process
	 */
	private void processClientAcknowledgement(Element ack) {
		if(isEnabled()) {
			if (ack.attribute("h") != null) {
				final long h = Long.valueOf(ack.attributeValue("h"));

				Log.debug( "Received acknowledgement from client: h={}", h );
				synchronized (this) {

					if ( !unacknowledgedServerStanzas.isEmpty() && h > unacknowledgedServerStanzas.getLast().x ) {
						Log.warn( "Client acknowledges stanzas that we didn't send! Client Ack h: {}, our last stanza: {}", h, unacknowledgedServerStanzas.getLast().x );
					}

					clientProcessedStanzas = h;

					// Remove stanzas from temporary storage as now acknowledged
					Log.trace( "Before processing client Ack (h={}): {} unacknowledged stanzas.", h, unacknowledgedServerStanzas.size() );

					// Pop all acknowledged stanzas.
					while( !unacknowledgedServerStanzas.isEmpty() && unacknowledgedServerStanzas.getFirst().x <= h )
					{
						unacknowledgedServerStanzas.removeFirst();
					}

					// Ensure that unacknowledged stanzas are purged after the client rolled over 'h' which occurs at h= (2^32)-1
					final int maxUnacked = getMaximumUnacknowledgedStanzas();
					final boolean clientHadRollOver = h < maxUnacked && !unacknowledgedServerStanzas.isEmpty() && unacknowledgedServerStanzas.getLast().x > mask - maxUnacked;
					if ( clientHadRollOver )
					{
						Log.info( "Client rolled over 'h'. Purging high-numbered unacknowledged stanzas." );
						while ( !unacknowledgedServerStanzas.isEmpty() && unacknowledgedServerStanzas.getLast().x > mask - maxUnacked)
						{
							unacknowledgedServerStanzas.removeLast();
						}
					}

					Log.trace( "After processing client Ack (h={}): {} unacknowledged stanzas.", h, unacknowledgedServerStanzas.size());
				}
			}
		}
	}

	/**
	 * Registers that Openfire sends a stanza to the client (which is expected to be acknowledged later).
	 * @param packet The stanza that is sent.
	 */
	public void sentStanza(Packet packet) {

		if(isEnabled()) {
			final long requestFrequency = JiveGlobals.getLongProperty( "stream.management.requestFrequency", 5 );
			final int size;

			synchronized (this)
			{
				// The next ID is one higher than the last stanza that was sent (which might be unacknowledged!)
				final long x = 1 + ( unacknowledgedServerStanzas.isEmpty() ? clientProcessedStanzas : unacknowledgedServerStanzas.getLast().x );
				unacknowledgedServerStanzas.addLast( new StreamManager.UnackedPacket( x, packet.createCopy() ) );

				size = unacknowledgedServerStanzas.size();

				Log.trace( "Added stanza of type '{}' to collection of unacknowledged stanzas (x={}). Collection size is now {}.", packet.getElement().getName(), x, size );

				// Prevent keeping to many stanzas in memory.
				if ( size > getMaximumUnacknowledgedStanzas() )
				{
					Log.warn( "To many stanzas go unacknowledged for this connection. Clearing queue and disabling functionality." );
					namespace = null;
					unacknowledgedServerStanzas.clear();
					return;
				}
			}

			// When we have a sizable amount of unacknowledged stanzas, request acknowledgement.
			if ( size % requestFrequency == 0 ) {
				Log.debug( "Requesting acknowledgement from peer, as we have {} or more unacknowledged stanzas.", requestFrequency );
				sendServerRequest();
			}
		}

	}

	public void onClose(PacketRouter router, JID serverAddress) {
		// Re-deliver unacknowledged stanzas from broken stream (XEP-0198)
		synchronized (this) {
			if(isEnabled()) {
				namespace = null; // disable stream management.
				for (StreamManager.UnackedPacket unacked : unacknowledgedServerStanzas) {
					if (unacked.packet instanceof Message) {
						Message m = (Message) unacked.packet;
						if (m.getExtension("delay", "urn:xmpp:delay") == null) {
							Element delayInformation = m.addChildElement("delay", "urn:xmpp:delay");
							delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.timestamp));
							delayInformation.addAttribute("from", serverAddress.toBareJID());
						}
						router.route(unacked.packet);
					}
				}
			}
		}

	}

	/**
	 * Determines whether Stream Management enabled for session this
	 * manager belongs to.
	 * @return true when stream management is enabled, otherwise false.
	 */
	public boolean isEnabled() {
		return namespace != null;
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
	 * The maximum amount of stanzas we keep, waiting for ack.
	 * @return The maximum number of stanzas.
	 */
	private int getMaximumUnacknowledgedStanzas()
	{
		return JiveGlobals.getIntProperty( "stream.management.max-unacked", 10000 );
	}
}
