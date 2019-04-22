package org.jivesoftware.openfire.stanzaid;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import java.util.Iterator;
import java.util.UUID;

/**
 * Utility methods that implement XEP-0359: Unique and Stable Stanza IDs.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0359.html>XEP-0359</a>
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class StanzaIDUtil
{
    private static final Logger Log = LoggerFactory.getLogger( StanzaIDUtil.class );

    /**
     * Modifies the stanza that's passed as a packet by adding a Stanza ID.
     *
     * @param packet The inbound packet (cannot be null).
     * @param self The ID of the 'local' entity that will generate the stanza ID (cannot be null).
     * @see <a href="https://xmpp.org/extensions/xep-0359.html>XEP-0359</a>
     */
    public static Packet ensureUniqueAndStableStanzaID( final Packet packet, final JID self )
    {
        if ( !JiveGlobals.getBooleanProperty( "xmpp.sid.enabled", true ) )
        {
            return packet;
        }

        if ( packet instanceof IQ && !JiveGlobals.getBooleanProperty( "xmpp.sid.iq.enabled", false ) )
        {
            return packet;
        }

        if ( packet instanceof Message && !JiveGlobals.getBooleanProperty( "xmpp.sid.message.enabled", true ) )
        {
            return packet;
        }

        if ( packet instanceof Presence && !JiveGlobals.getBooleanProperty( "xmpp.sid.presence.enabled", false ) )
        {
            return packet;
        }

        final Element parentElement;
        if ( packet instanceof IQ ) {
            parentElement = ((IQ) packet).getChildElement();
        } else {
            parentElement = packet.getElement();
        }

        // The packet likely is an IQ result or error, which can, but are not required to have a child element.
        // To have a consistent behavior for these, we'll not add a stanza-ID here.
        if ( parentElement == null )
        {
            Log.debug( "Unable to find appropriate element. Not adding stanza-id to packet: {}", packet );
            return packet;
        }

        // Stanza ID generating entities, which encounter a <stanza-id/> element where the 'by' attribute matches the 'by'
        // attribute they would otherwise set, MUST delete that element even if they are not adding their own stanza ID.
        final Iterator<Element> existingElementIterator = parentElement.elementIterator( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        while (existingElementIterator.hasNext()) {
            final Element element = existingElementIterator.next();

            if (self.toString().equals( element.attributeValue( "by" ) ) ) {
                Log.warn( "Removing a 'stanza-id' element from an inbound stanza, as its 'by' attribute value matches the value that we would set. Offending stanza: {}", packet );
                existingElementIterator.remove();
            }
        }

        final String id = generateUniqueAndStableStanzaID( packet );

        final Element stanzaIdElement = parentElement.addElement( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        stanzaIdElement.addAttribute( "id", id );
        stanzaIdElement.addAttribute( "by", self.toString() );

        return packet;
    }

    /**
     * Returns a value that is an appropriate unique and stable stanza ID in
     * context of XEP-0359: it's either the origin-id value, or a UUID.
     *
     * @param packet The stanza for what to return the ID (cannot be null).
     * @return The ID (never null or empty string).
     */
    public static String generateUniqueAndStableStanzaID( final Packet packet )
    {
        String result = null;

        final Iterator<Element> existingElementIterator = packet.getElement().elementIterator( QName.get( "origin-id", "urn:xmpp:sid:0" ) );
        while (existingElementIterator.hasNext() && (result == null || result.isEmpty() ) )
        {
            final Element element = existingElementIterator.next();
            result = element.attributeValue( "id" );
        }

        if ( result == null || result.isEmpty() ) {
            result = UUID.randomUUID().toString();
            Log.debug( "Using newly generated value '{}' for stanza that has id '{}'.", result, packet.getID() );
        } else {
            Log.debug( "Using origin-id provided value '{}' for stanza that has id '{}'.", result, packet.getID() );
        }

        return result;
    }
}
