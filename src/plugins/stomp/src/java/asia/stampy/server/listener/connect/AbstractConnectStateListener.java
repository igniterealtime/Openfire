/*
 * Copyright (C) 2013 Burton Alexander
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 * 
 */
package asia.stampy.server.listener.connect;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;

/**
 * This class ensures that a {@link StompMessageType#CONNECT} or.
 * 
 * {@link StompMessageType#STOMP} frame is the first frame a client sends, that
 * no additional connect frames are sent, and that a
 * {@link StompMessageType#DISCONNECT} frame initializes the state.<br>
 * <br>
 */
@StampyLibrary(libraryName="stampy-client-server")
public abstract class AbstractConnectStateListener<SVR extends AbstractStampyMessageGateway> implements
    StampyMessageListener {

  protected Queue<HostPort> connectedClients = new ConcurrentLinkedQueue<HostPort>();
  private SVR gateway;

  private static StompMessageType[] TYPES = StompMessageType.values();

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.gateway.StampyMessageListener#getMessageTypes()
   */
  @Override
  public StompMessageType[] getMessageTypes() {
    return TYPES;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * asia.stampy.common.gateway.StampyMessageListener#isForMessage(asia.stampy
   * .common.message.StampyMessage)
   */
  @Override
  public boolean isForMessage(StampyMessage<?> message) {
    return true;
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.gateway.StampyMessageListener#messageReceived(asia.
   * stampy.common.message.StampyMessage, asia.stampy.common.HostPort)
   */
  @Override
  public void messageReceived(StampyMessage<?> message, HostPort hostPort) throws Exception {
    switch (message.getMessageType()) {
    case ABORT:
    case ACK:
    case BEGIN:
    case COMMIT:
    case NACK:
    case SEND:
    case SUBSCRIBE:
    case UNSUBSCRIBE:
      checkConnected(hostPort);
      break;
    case CONNECT:
    case STOMP:
      checkDisconnected(hostPort);
      connectedClients.add(hostPort);
      break;
    case DISCONNECT:
      connectedClients.remove(hostPort);
      break;
    default:
      throw new IllegalArgumentException("Unexpected message type " + message.getMessageType());

    }

  }

  private void checkDisconnected(HostPort hostPort) throws AlreadyConnectedException {
    if (!connectedClients.contains(hostPort)) return;

    throw new AlreadyConnectedException(hostPort + " is already connected");
  }

  private void checkConnected(HostPort hostPort) throws NotConnectedException {
    if (connectedClients.contains(hostPort)) return;

    throw new NotConnectedException("CONNECT message required for " + hostPort);
  }

  /**
   * Gets the gateway.
   * 
   * @return the gateway
   */
  public SVR getGateway() {
    return gateway;
  }

  /**
   * Inject the {@link AbstractStampyMessageGateway} on system startup.
   * 
   * @param gateway
   *          the new gateway
   */
  public void setGateway(SVR gateway) {
    this.gateway = gateway;
    ensureCleanup();
  }

  /**
   * Configure the gateway to clean up the queue of connected clients on session
   * termination.
   */
  protected abstract void ensureCleanup();

}
