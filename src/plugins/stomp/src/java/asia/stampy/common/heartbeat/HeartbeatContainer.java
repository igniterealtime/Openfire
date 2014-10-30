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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import asia.stampy.common.gateway.HostPort;

/**
 * Encapsulates all the currently active {@link PaceMaker}s. This class is a
 * singleton; wire into the system appropriately.
 */
@Resource
@StampyLibrary(libraryName = "stampy-client-server")
public class HeartbeatContainer implements StampyHeartbeatContainer {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Map<HostPort, PaceMaker> paceMakers = new ConcurrentHashMap<HostPort, PaceMaker>();

  /* (non-Javadoc)
   * @see asia.stampy.common.heartbeat.StampyHeartbeatContainer#start(asia.stampy.common.gateway.HostPort, asia.stampy.common.gateway.AbstractStampyMessageGateway, int)
   */
  @Override
  public void start(HostPort hostPort, AbstractStampyMessageGateway gateway, int timeMillis) {
    PaceMaker paceMaker = new PaceMaker(timeMillis);
    paceMaker.setHostPort(hostPort);
    paceMaker.setGateway(gateway);
    paceMaker.start();

    add(hostPort, paceMaker);
  }

  /* (non-Javadoc)
   * @see asia.stampy.common.heartbeat.StampyHeartbeatContainer#stop(asia.stampy.common.gateway.HostPort)
   */
  @Override
  public void stop(HostPort hostPort) {
    PaceMaker paceMaker = paceMakers.get(hostPort);
    if (paceMaker != null) {
      log.info("Stopping PaceMaker for {}", hostPort);
      paceMaker.stop();
    }
  }

  /**
   * Adds a new {@link PaceMaker} for the specified {@link HostPort}.
   * 
   * @param hostPort
   *          the host port
   * @param paceMaker
   *          the pace maker
   */
  protected void add(HostPort hostPort, PaceMaker paceMaker) {
    stop(hostPort);
    log.info("Adding PaceMaker for {}", hostPort);
    paceMakers.put(hostPort, paceMaker);
  }

  /* (non-Javadoc)
   * @see asia.stampy.common.heartbeat.StampyHeartbeatContainer#remove(asia.stampy.common.gateway.HostPort)
   */
  @Override
  public void remove(HostPort hostPort) {
    stop(hostPort);
    log.info("Removing PaceMaker for {}", hostPort);
    paceMakers.remove(hostPort);
  }

  /* (non-Javadoc)
   * @see asia.stampy.common.heartbeat.StampyHeartbeatContainer#reset(asia.stampy.common.gateway.HostPort)
   */
  @Override
  public void reset(HostPort hostPort) {
    if (hostPort == null) return;
    log.trace("Resetting PaceMaker for {}", hostPort);
    PaceMaker paceMaker = paceMakers.get(hostPort);
    if (paceMaker != null) paceMaker.reset();
  }
}
