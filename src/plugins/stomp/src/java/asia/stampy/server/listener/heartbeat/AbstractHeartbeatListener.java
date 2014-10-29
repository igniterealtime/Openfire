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
package asia.stampy.server.listener.heartbeat;

import java.lang.invoke.MethodHandles;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.client.message.connect.ConnectHeader;
import asia.stampy.client.message.connect.ConnectMessage;
import asia.stampy.client.message.stomp.StompMessage;
import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.heartbeat.HeartbeatContainer;
import asia.stampy.common.heartbeat.StampyHeartbeatContainer;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;

/**
 * This class intercepts incoming {@link StompMessageType#CONNECT} from a STOMP
 * 1.2 client and starts a heartbeat, if requested.<br>
 * <br>
 * 
 * <i>CONNECT heart-beat:[cx],[cy] <br>
 * CONNECTED: heart-beat:[sx],[sy]<br>
 * <br>
 * For heart-beats from the client to the server: if [cx] is 0 (the client
 * cannot send heart-beats) or [sy] is 0 (the server does not want to receive
 * heart-beats) then there will be none otherwise, there will be heart-beats
 * every MAX([cx],[sy]) milliseconds In the other direction, [sx] and [cy] are
 * used the same way.</i>
 * 
 * @see HeartbeatContainer
 * @see PaceMaker
 */
@StampyLibrary(libraryName = "stampy-client-server")
public abstract class AbstractHeartbeatListener<SVR extends AbstractStampyMessageGateway> implements
    StampyMessageListener {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static StompMessageType[] TYPES = { StompMessageType.CONNECT, StompMessageType.STOMP,
      StompMessageType.DISCONNECT };

  private StampyHeartbeatContainer heartbeatContainer;

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
    return StringUtils.isNotEmpty(message.getHeader().getHeaderValue(ConnectHeader.HEART_BEAT))
        || isDisconnectMessage(message);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.gateway.StampyMessageListener#messageReceived(asia.
   * stampy.common.message.StampyMessage, asia.stampy.common.HostPort)
   */
  @Override
  public void messageReceived(StampyMessage<?> message, HostPort hostPort) throws Exception {
    if (isDisconnectMessage(message)) {
      getHeartbeatContainer().remove(hostPort);
      return;
    }

    ConnectHeader header = getConnectHeader(message);

    int requested = header.getIncomingHeartbeat();
    if (getGateway().getHeartbeat() <= 0 || requested <= 0) return;

    int heartbeat = Math.max(requested, getGateway().getHeartbeat());

    log.info("Starting heartbeats for {} at {} ms intervals", hostPort, heartbeat);

    getHeartbeatContainer().start(hostPort, getGateway(), heartbeat);
  }

  /**
   * Reset heartbeat.
   * 
   * @param hostPort
   *          the host port
   */
  public void resetHeartbeat(HostPort hostPort) {
    getHeartbeatContainer().reset(hostPort);
  }

  private ConnectHeader getConnectHeader(StampyMessage<?> message) {
    return isConnectMessage(message) ? ((ConnectMessage) message).getHeader() : ((StompMessage) message).getHeader();
  }

  private boolean isConnectMessage(StampyMessage<?> message) {
    return StompMessageType.CONNECT.equals(message.getMessageType());
  }

  private boolean isDisconnectMessage(StampyMessage<?> message) {
    return StompMessageType.DISCONNECT.equals(message.getMessageType());
  }

  /**
   * Gets the heartbeat container.
   * 
   * @return the heartbeat container
   */
  public StampyHeartbeatContainer getHeartbeatContainer() {
    return heartbeatContainer;
  }

  /**
   * Inject the {@link HeartbeatContainer} on system startup.
   * 
   * @param heartbeatContainer
   *          the new heartbeat container
   */
  public void setHeartbeatContainer(StampyHeartbeatContainer heartbeatContainer) {
    this.heartbeatContainer = heartbeatContainer;
  }

  /**
   * Gets the message gateway.
   * 
   * @return the message gateway
   */
  public SVR getGateway() {
    return gateway;
  }

  /**
   * Inject the server {@link AbstractStampyMessageGateway} on system startup.
   * 
   * @param gateway
   *          the new message gateway
   */
  public void setGateway(SVR gateway) {
    this.gateway = gateway;
  }

}
