package org.jivesoftware.messenger;

/**
 *
 *
 * @author Matt Tucker
 */
public interface RoutableChannelHandler extends ChannelHandler {

    /**
      * Returns the XMPP address. The address is used by services like the core
      * server packet router to determine if a packet should be sent to the handler.
      * Handlers that are working on behalf of the server should use the generic server
      * hostname address (e.g. server.com).
      *
      * @return the XMPP address.
      */
     public XMPPAddress getAddress();
}
