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
package asia.stampy.common.gateway;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;
import asia.stampy.common.message.interceptor.InterceptException;
import asia.stampy.common.message.interceptor.StampyOutgoingMessageInterceptor;
import asia.stampy.common.message.interceptor.StampyOutgoingTextInterceptor;

/**
 * A StampyMessageGateway is the interface between the technology used to
 * connect to a STOMP implementation and the Stampy library. It is the class
 * through which STOMP messages are sent and received.<br>
 * <br>
 * Subclasses are singletons; wire into the system appropriately.
 */
@StampyLibrary(libraryName="stampy-client-server")
public abstract class AbstractStampyMessageGateway {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** The interceptors. */
  protected Queue<StampyOutgoingMessageInterceptor> interceptors = new ConcurrentLinkedQueue<StampyOutgoingMessageInterceptor>();

  /** The text interceptors. */
  protected Queue<StampyOutgoingTextInterceptor> textInterceptors = new ConcurrentLinkedQueue<StampyOutgoingTextInterceptor>();

  private List<StampyMessageListener> listeners = Collections.synchronizedList(new ArrayList<StampyMessageListener>());

  private Lock stampyInterceptorLock = new ReentrantLock(true);
  private Lock textInterceptorLock = new ReentrantLock(true);

  private boolean autoShutdown;

  private int heartbeat;

  private UnparseableMessageHandler unparseableMessageHandler = new DefaultUnparseableMessageHandler();

  private int port;

  private int maxMessageSize = Integer.MAX_VALUE;

  /**
   * Broadcasts a {@link StampyMessage} to all connected clients from the server
   * or to the server from a client. Use this method for all STOMP messages.
   * 
   * @param message
   *          the message
   * @throws InterceptException
   *           the intercept exception
   */
  public void broadcastMessage(StampyMessage<?> message) throws InterceptException {
    interceptOutgoingMessage(message);
    broadcastMessage(message.toStompMessage(true));
  }

  /**
   * Adds the specified outgoing message interceptor.
   * 
   * @param interceptor
   *          the interceptor
   * @see StampyOutgoingMessageInterceptor
   */
  public void addOutgoingMessageInterceptor(StampyOutgoingMessageInterceptor interceptor) {
    interceptors.add(interceptor);
  }

  /**
   * Removes the specified outgoing message interceptor.
   * 
   * @param interceptor
   *          the interceptor
   * @see StampyOutgoingMessageInterceptor
   */
  public void removeOutgoingMessageInterceptor(StampyOutgoingMessageInterceptor interceptor) {
    interceptors.remove(interceptor);
  }

  /**
   * Adds the specified outgoing message interceptors. For use by DI frameworks.
   * 
   * @param interceptors
   *          the new outgoing message interceptors
   * @see StampyOutgoingMessageInterceptor
   */
  public void setOutgoingMessageInterceptors(Collection<StampyOutgoingMessageInterceptor> interceptors) {
    this.interceptors.addAll(interceptors);
  }

  /**
   * Adds the specified outgoing message interceptor.
   * 
   * @param interceptor
   *          the interceptor
   * @see StampyOutgoingMessageInterceptor
   */
  public void addOutgoingTextInterceptor(StampyOutgoingTextInterceptor interceptor) {
    textInterceptors.add(interceptor);
  }

  /**
   * Removes the specified outgoing message interceptor.
   * 
   * @param interceptor
   *          the interceptor
   * @see StampyOutgoingMessageInterceptor
   */
  public void removeOutgoingTextInterceptor(StampyOutgoingTextInterceptor interceptor) {
    textInterceptors.remove(interceptor);
  }

  /**
   * Adds the specified outgoing message interceptors. For use by DI frameworks.
   * 
   * @param interceptors
   *          the new outgoing text interceptors
   * @see StampyOutgoingMessageInterceptor
   */
  public void setOutgoingTextInterceptors(Collection<StampyOutgoingTextInterceptor> interceptors) {
    this.textInterceptors.addAll(interceptors);
  }

  /**
   * Sends a {@link StampyMessage} to the specified {@link HostPort}. Use this
   * method for all STOMP messages.
   * 
   * @param message
   *          the message
   * @param hostPort
   *          the host port
   * @throws InterceptException
   *           the intercept exception
   */
  public void sendMessage(StampyMessage<?> message, HostPort hostPort) throws InterceptException {
    interceptOutgoingMessage(message, hostPort);
    sendMessage(message.toStompMessage(true), hostPort);
  }

  /**
   * Intercept outgoing message.
   * 
   * @param message
   *          the message
   * @throws InterceptException
   *           the intercept exception
   */
  protected final void interceptOutgoingMessage(StampyMessage<?> message) throws InterceptException {
    stampyInterceptorLock.lock();
    try {
      for (StampyOutgoingMessageInterceptor interceptor : interceptors) {
        if (isForType(interceptor.getMessageTypes(), message.getMessageType()) && interceptor.isForMessage(message)) {
          interceptor.interceptMessage(message);
        }
      }
    } finally {
      stampyInterceptorLock.unlock();
    }
  }

  /**
   * Intercept outgoing message.
   * 
   * @param message
   *          the message
   * @param hostPort
   *          the host port
   * @throws InterceptException
   *           the intercept exception
   */
  protected final void interceptOutgoingMessage(StampyMessage<?> message, HostPort hostPort) throws InterceptException {
    stampyInterceptorLock.lock();
    try {
      for (StampyOutgoingMessageInterceptor interceptor : interceptors) {
        if (isForType(interceptor.getMessageTypes(), message.getMessageType()) && interceptor.isForMessage(message)) {
          interceptor.interceptMessage(message, hostPort);
        }
      }
    } finally {
      stampyInterceptorLock.unlock();
    }
  }

  /**
   * Intercept outgoing message.
   * 
   * @param message
   *          the message
   * @throws InterceptException
   *           the intercept exception
   */
  protected final void interceptOutgoingMessage(String message) throws InterceptException {
    textInterceptorLock.lock();
    try {
      for (StampyOutgoingTextInterceptor interceptor : textInterceptors) {
        interceptor.interceptMessage(message);
      }
    } finally {
      textInterceptorLock.unlock();
    }
  }

  /**
   * Checks if is for type.
   * 
   * @param messageTypes
   *          the message types
   * @param messageType
   *          the message type
   * @return true, if is for type
   */
  protected boolean isForType(StompMessageType[] messageTypes, StompMessageType messageType) {
    if (messageTypes == null || messageTypes.length == 0) return false;

    for (StompMessageType type : messageTypes) {
      if (type.equals(messageType)) return true;
    }

    return false;
  }

  /**
   * Notify listeners of received {@link StampyMessage}s.
   * 
   * @param sm
   *          the sm
   * @param hostPort
   *          the host port
   * @throws Exception
   *           the exception
   */
  public void notifyMessageListeners(StampyMessage<?> sm, HostPort hostPort) throws Exception {
    for (StampyMessageListener listener : listeners) {
      if (isForType(listener.getMessageTypes(), sm.getMessageType()) && listener.isForMessage(sm)) {
        log.trace("Evaluating message {} with listener {}", sm, listener);
        listener.messageReceived(sm, hostPort);
      }
    }
  }

  /**
   * Adds the message listener.
   * 
   * @param listener
   *          the listener
   */
  public final void addMessageListener(StampyMessageListener listener) {
    if (listeners.size() == 0 && !(listener instanceof SecurityMessageListener)) {
      throw new StampySecurityException();
    }

    listeners.add(listener);
  }
  
  public final void addMessageListener(StampyMessageListener listener, int idx) {
    if(idx == 0 && !(listener instanceof SecurityMessageListener)) {
      throw new StampySecurityException();
    }
    
    listeners.add(idx, listener);
  }

  /**
   * Removes the message listener.
   * 
   * @param listener
   *          the listener
   */
  public void removeMessageListener(StampyMessageListener listener) {
    listeners.remove(listener);
  }

  /**
   * Clear message listeners.
   */
  public void clearMessageListeners() {
    listeners.clear();
  }

  /**
   * Sets the listeners.
   * 
   * @param listeners
   *          the new listeners
   */
  public void setListeners(Collection<StampyMessageListener> listeners) {
    this.listeners.addAll(listeners);
  }

  /**
   * Broadcasts the specified String to all connections. Included for STOMP
   * implementations which accept custom message types. Use for all non-STOMP
   * messages.
   * 
   * @param stompMessage
   *          the stomp message
   * @throws InterceptException
   *           the intercept exception
   */
  public abstract void broadcastMessage(String stompMessage) throws InterceptException;

  /**
   * Sends the specified String to the specified {@link HostPort}. Included for
   * STOMP implementations which accept custom message types. Use for all
   * non-STOMP messages.
   * 
   * @param stompMessage
   *          the stomp message
   * @param hostPort
   *          the host port
   * @throws InterceptException
   *           the intercept exception
   */
  public abstract void sendMessage(String stompMessage, HostPort hostPort) throws InterceptException;

  /**
   * Closes the connection to the STOMP server or client.
   * 
   * @param hostPort
   *          the host port
   */
  public abstract void closeConnection(HostPort hostPort);

  /**
   * Connects to a STOMP server or client as specified by configuration.
   * 
   * @throws Exception
   *           the exception
   */
  public abstract void connect() throws Exception;

  /**
   * Shuts down the underlying connection technology.
   * 
   * @throws Exception
   *           the exception
   */
  public abstract void shutdown() throws Exception;

  /**
   * Returns true if a connection exists and is active.
   * 
   * @param hostPort
   *          the host port
   * @return true, if is connected
   */
  public abstract boolean isConnected(HostPort hostPort);

  /**
   * Gets the connected host ports.
   * 
   * @return the connected host ports
   */
  public abstract Set<HostPort> getConnectedHostPorts();

  /**
   * If true the gateway will shut down when all sessions are terminated.
   * Typically clients will be set to true, servers to false (the default).
   * 
   * @return true, if is auto shutdown
   */
  public boolean isAutoShutdown() {
    return autoShutdown;
  }

  /**
   * Sets the auto shutdown.
   * 
   * @param autoShutdown
   *          the new auto shutdown
   */
  public void setAutoShutdown(boolean autoShutdown) {
    this.autoShutdown = autoShutdown;
  }

  /**
   * Gets the heartbeat.
   * 
   * @return the heartbeat
   */
  public int getHeartbeat() {
    return heartbeat;
  }

  /**
   * Sets the heartbeat.
   * 
   * @param heartbeat
   *          the new heartbeat
   */
  public void setHeartbeat(int heartbeat) {
    this.heartbeat = heartbeat;
  }

  /**
   * Returns the {@link UnparseableMessageHandler}, defaults to.
   * 
   * @return the unparseable message handler
   *         {@link DefaultUnparseableMessageHandler}.
   */
  public UnparseableMessageHandler getUnparseableMessageHandler() {
    return unparseableMessageHandler;
  }

  /**
   * Inject the appropriate {@link UnparseableMessageHandler} on system startup.
   * 
   * @param unparseableMessageHandler
   *          the new unparseable message handler
   */
  public void setUnparseableMessageHandler(UnparseableMessageHandler unparseableMessageHandler) {
    this.unparseableMessageHandler = unparseableMessageHandler;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  public void setMaxMessageSize(int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }
}
