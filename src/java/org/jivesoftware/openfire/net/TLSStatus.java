/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software and Artur Hefczyc. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.net;

/**
 * A TLSStatus enum describing the current handshaking state of this TLS connection.
 * 
 * @author Artur Hefczyc
 * @author Hao Chen
 */
public enum TLSStatus {

	/**
	 * ust send data to the remote side before handshaking can continue.
	 */
	NEED_WRITE,

	/**
	 * Need to receive data from the remote side before handshaking can continue.
	 */
	NEED_READ,

	/**
	 * Not be able to unwrap the incoming data because there were not enough source bytes available
	 * to make a complete packet.
	 */
	UNDERFLOW,

	/**
	 * The operation just closed this side of the SSLEngine, or the operation could not be completed
	 * because it was already closed.
	 */
	CLOSED,

	/**
	 * Handshaking is OK.
	 */
	OK;
}
