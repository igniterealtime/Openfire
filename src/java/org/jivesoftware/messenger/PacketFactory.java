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

package org.jivesoftware.messenger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * A factory to produce packets in one of the three XMPP flavors:
 * iq, presence, or message.
 *
 * @author Iain Shigeoka
 */
public interface PacketFactory {

    /**
     * Create an empty message.
     *
     * @return an empty message packet.
     */
    Message getMessage();

    /**
     * Create a message parsed from the given stream.
     *
     * @param xpp the stream reader.
     * @return a message produced from the reader.
     * @throws XMLStreamException if there was trouble reading the stream.
     */
    Message getMessage(XMLStreamReader xpp) throws XMLStreamException;

    /**
     * Create a message with the given body text.
     *
     * @param msgText the message body text.
     * @return a message with body text.
     * @throws XMLStreamException if there was trouble reading the stream.
     */
    Message getMessage(String msgText) throws XMLStreamException;

    /**
     * Create an empty iq packet.
     *
     * @return an empty iq packet.
     */
    IQ getIQ();

    /**
     * Create an IQ packet from the given stream.
     *
     * @param xpp the stream to read the iq packet from.
     * @return the iq packet created.
     * @throws XMLStreamException if there was trouble reading the stream.
     */
    IQ getIQ(XMLStreamReader xpp) throws XMLStreamException;

    /**
     * Create an empty presence packet.
     *
     * @return an empty presence packet.
     */
    Presence getPresence();

    /**
     * Create a presence packet from the given stream.
     *
     * @param xpp the stream to read the presence packet from.
     * @return the packet created.
     * @throws XMLStreamException if there was trouble reading the stream.
     */
    Presence getPresence(XMLStreamReader xpp) throws XMLStreamException;
}