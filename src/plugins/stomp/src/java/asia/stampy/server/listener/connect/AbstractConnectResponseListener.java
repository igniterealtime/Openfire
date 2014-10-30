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

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.client.message.connect.ConnectHeader;
import asia.stampy.client.message.connect.ConnectMessage;
import asia.stampy.client.message.stomp.StompMessage;
import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;
import asia.stampy.common.message.interceptor.InterceptException;
import asia.stampy.server.message.connected.ConnectedMessage;

/**
 * This class sends a CONNECTED response to a CONNECT or STOMP message.
 */
@StampyLibrary(libraryName = "stampy-client-server")
public abstract class AbstractConnectResponseListener<SVR extends AbstractStampyMessageGateway> implements
    StampyMessageListener {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static StompMessageType[] TYPES = { StompMessageType.CONNECT, StompMessageType.STOMP };

  private SVR gateway;

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
    case CONNECT:
      sendConnected(((ConnectMessage) message).getHeader(), hostPort);
      return;
    case STOMP:
      sendConnected(((StompMessage) message).getHeader(), hostPort);
      return;
    default:
      return;
    }
  }

  private void sendConnected(ConnectHeader header, HostPort hostPort) throws InterceptException {
    log.debug("Sending connected message to {}", hostPort);
    ConnectedMessage message = new ConnectedMessage("1.2");

    int requested = message.getHeader().getIncomingHeartbeat();
    if (requested >= 0 || getGateway().getHeartbeat() >= 0) {
      int heartbeat = Math.max(requested, getGateway().getHeartbeat());
      message.getHeader().setHeartbeat(heartbeat, header.getOutgoingHeartbeat());
    }
    message.getHeader().setSession(hostPort.toString());

    getGateway().sendMessage(message, hostPort);
    log.debug("Sent connected message to {}", hostPort);
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
  }

}
