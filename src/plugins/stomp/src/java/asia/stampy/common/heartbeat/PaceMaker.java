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
package asia.stampy.common.heartbeat;

import java.lang.invoke.MethodHandles;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import asia.stampy.common.gateway.HostPort;

/**
 * Sends heartbeats to a remote connection as specified by the STOMP
 * specification.
 */
@StampyLibrary(libraryName="stampy-client-server")
class PaceMaker {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private long timeInMillis;
  private TimerTask stopwatch;
  private Timer timer = new Timer("Stampy PaceMaker", true);

  private AbstractStampyMessageGateway gateway;

  private HostPort hostPort;

  private int heartbeatCount;

  /**
   * Instantiates a new pace maker.
   * 
   * @param timeInMillis
   *          the time in millis
   */
  public PaceMaker(int timeInMillis) {
    this.timeInMillis = timeInMillis;
  }

  /**
   * Reset.
   */
  public synchronized void reset() {
    log.trace("PaceMaker reset invoked");
    setHeartbeatCount(0);
    stop();
    start();
  }

  /**
   * Stop.
   */
  public void stop() {
    log.trace("PaceMaker stop invoked");
    if (stopwatch != null) {
      stopwatch.cancel();
      timer.purge();
    }
  }

  /**
   * Start.
   */
  public void start() {
    log.trace("PaceMaker start invoked for sleep time of {} ms", getSleepTime());
    stopwatch = new TimerTask() {

      @Override
      public void run() {
        executeHeartbeat();
      }
    };

    timer.schedule(stopwatch, getSleepTime());
  }

  private void executeHeartbeat() {
    if (heartbeatCount >= 2) {
      log.warn("No response after 2 heartbeats, closing connection");
      gateway.closeConnection(getHostPort());
    } else {
      try {
        if (gateway.isConnected(getHostPort())) {
          gateway.sendMessage(StampyHeartbeatContainer.HB1, getHostPort());
          log.debug("Sent heartbeat");
          start();
          heartbeatCount++;
        }
      } catch (Exception e) {
        log.error("Could not send heartbeat", e);
      }
    }
  }

  /**
   * Gets the sleep time.
   * 
   * @return the sleep time
   */
  public long getSleepTime() {
    return timeInMillis;
  }

  /**
   * Gets the message gateway.
   * 
   * @return the message gateway
   */
  public AbstractStampyMessageGateway getGateway() {
    return gateway;
  }

  /**
   * Sets the message gateway. Must be called upon instantiation.
   * 
   * @param gateway
   *          the new message gateway
   */
  public void setGateway(AbstractStampyMessageGateway gateway) {
    this.gateway = gateway;
  }

  /**
   * Gets the host port.
   * 
   * @return the host port
   */
  public HostPort getHostPort() {
    return hostPort;
  }

  /**
   * Sets the host port. Must be called upon instantiation.
   * 
   * @param hostPort
   *          the new host port
   */
  public void setHostPort(HostPort hostPort) {
    this.hostPort = hostPort;
  }

  /**
   * Gets the heartbeat count. If the count reaches 2 then the connection is
   * assumed to be dead and the connection to the remote host is closed.
   * 
   * @return the heartbeat count
   */
  public int getHeartbeatCount() {
    return heartbeatCount;
  }

  /**
   * Sets the heartbeat count.
   * 
   * @param heartbeatCount
   *          the new heartbeat count
   */
  public void setHeartbeatCount(int heartbeatCount) {
    this.heartbeatCount = heartbeatCount;
  }

}
