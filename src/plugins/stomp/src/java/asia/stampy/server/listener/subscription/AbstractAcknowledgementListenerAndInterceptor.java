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
package asia.stampy.server.listener.subscription;

import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang.StringUtils;

import asia.stampy.client.message.ack.AckHeader;
import asia.stampy.client.message.ack.AckMessage;
import asia.stampy.client.message.nack.NackHeader;
import asia.stampy.client.message.nack.NackMessage;
import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;
import asia.stampy.common.message.interceptor.AbstractOutgoingMessageInterceptor;
import asia.stampy.common.message.interceptor.InterceptException;
import asia.stampy.server.message.message.MessageMessage;

/**
 * This class assists in the publication of {@link StompMessageType#MESSAGE}
 * messages for a subscription. If confirmation of the publication is requested
 * a timer is created to await receipt of the confirmation, and the appropriate
 * methods of the {@link StampyAcknowledgementHandler} implementation are
 * invoked.
 */
@StampyLibrary(libraryName = "stampy-client-server")
public abstract class AbstractAcknowledgementListenerAndInterceptor<SVR extends AbstractStampyMessageGateway> extends
    AbstractOutgoingMessageInterceptor<SVR> implements StampyMessageListener {
  private static final StompMessageType[] TYPES = { StompMessageType.ACK, StompMessageType.NACK,
      StompMessageType.MESSAGE };

  private StampyAcknowledgementHandler handler;

  /** The messages. */
  protected Map<HostPort, Queue<String>> messages = new ConcurrentHashMap<HostPort, Queue<String>>();

  private Timer ackTimer = new Timer("Stampy Acknowledgement Timer", true);

  private long ackTimeoutMillis = 60000;

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
    switch (message.getMessageType()) {
    case MESSAGE:
      return StringUtils.isNotEmpty(((MessageMessage) message).getHeader().getAck());
    case ACK:
    case NACK:
      return true;
    default:
      return false;
    }
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
    case ACK:
      evaluateAck(((AckMessage) message).getHeader(), hostPort);
      break;
    case NACK:
      evaluateNack(((NackMessage) message).getHeader(), hostPort);
      break;
    default:
      break;

    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * asia.stampy.common.message.interceptor.StampyOutgoingMessageInterceptor
   * #interceptMessage(asia.stampy.common.message.StampyMessage,
   * asia.stampy.common.HostPort)
   */
  @Override
  public void interceptMessage(StampyMessage<?> message, HostPort hostPort) throws InterceptException {
    MessageMessage msg = (MessageMessage) message;

    String ack = msg.getHeader().getAck();

    Queue<String> queue = messages.get(hostPort);
    if (queue == null) {
      queue = new ConcurrentLinkedQueue<String>();
      messages.put(hostPort, queue);
    }

    queue.add(ack);
    startTimerTask(hostPort, ack);
  }

  private void startTimerTask(final HostPort hostPort, final String ack) {
    TimerTask task = new TimerTask() {

      @Override
      public void run() {
        Queue<String> q = messages.get(hostPort);
        if (q == null || !q.contains(ack)) return;

        getHandler().noAcknowledgementReceived(ack);
        q.remove(ack);
      }
    };

    ackTimer.schedule(task, getAckTimeoutMillis());
  }

  private void evaluateNack(NackHeader header, HostPort hostPort) throws Exception {
    String id = header.getId();
    if (hasMessageAck(id, hostPort)) {
      clearMessageAck(id, hostPort);
      getHandler().nackReceived(id, header.getReceipt(), header.getTransaction());
    } else {
      throw new UnexpectedAcknowledgementException("No NACK message expected, yet received id " + id + " from "
          + hostPort);
    }
  }

  private void evaluateAck(AckHeader header, HostPort hostPort) throws Exception {
    String id = header.getId();
    if (hasMessageAck(id, hostPort)) {
      clearMessageAck(id, hostPort);
      getHandler().ackReceived(id, header.getReceipt(), header.getTransaction());
    } else {
      throw new UnexpectedAcknowledgementException("No ACK message expected, yet received id " + id + " from "
          + hostPort);
    }
  }

  private boolean hasMessageAck(String messageId, HostPort hostPort) {
    Queue<String> ids = messages.get(hostPort);
    if (ids == null || ids.isEmpty()) return false;

    return ids.contains(messageId);
  }

  private void clearMessageAck(String messageId, HostPort hostPort) {
    Queue<String> ids = messages.get(hostPort);
    if (ids == null) return;

    ids.remove(messageId);
  }

  /**
   * Gets the ack timeout millis.
   * 
   * @return the ack timeout millis
   */
  public long getAckTimeoutMillis() {
    return ackTimeoutMillis;
  }

  /**
   * Sets the ack timeout millis. Initialize appropriately on system startup.
   * Defaults to 60 seconds (60000).
   * 
   * @param ackTimeoutMillis
   *          the new ack timeout millis
   */
  public void setAckTimeoutMillis(long ackTimeoutMillis) {
    this.ackTimeoutMillis = ackTimeoutMillis;
  }

  /**
   * Sets the gateway.
   * 
   * @param gateway
   *          the new gateway
   */
  public void setGateway(SVR gateway) {
    super.setGateway(gateway);
    ensureCleanup();
  }

  /**
   * Configure the gateway to clean up the map of expected acks on session
   * termination.
   */
  protected abstract void ensureCleanup();

  /**
   * Gets the handler.
   * 
   * @return the handler
   */
  public StampyAcknowledgementHandler getHandler() {
    return handler;
  }

  /**
   * Inject the {@link StampyAcknowledgementHandler} implementation on system
   * startup.
   * 
   * @param handler
   *          the new handler
   */
  public void setHandler(StampyAcknowledgementHandler handler) {
    this.handler = handler;
  }

}
