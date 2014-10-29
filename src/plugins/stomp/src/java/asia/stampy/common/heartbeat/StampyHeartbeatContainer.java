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

import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import asia.stampy.common.gateway.HostPort;

public interface StampyHeartbeatContainer {

  /** The Constant HB1. */
  public static final String HB1 = "\n";
  /** The Constant HB2. */
  public static final String HB2 = "\r\n";

  /**
   * Starts a heartbeat for the specified host & port.
   * @param hostPort
   * @param gateway
   * @param timeMillis
   */
  public abstract void start(HostPort hostPort, AbstractStampyMessageGateway gateway, int timeMillis);

  /**
   * Stops heartbeats to the specified {@link HostPort}.
   * 
   * @param hostPort
   *          the host port
   */
  public abstract void stop(HostPort hostPort);

  /**
   * Removes the {@link PaceMaker} specified by {@link HostPort}.
   * 
   * @param hostPort
   *          the host port
   */
  public abstract void remove(HostPort hostPort);

  /**
   * Resets the {@link PaceMaker} for the specified {@link HostPort}, preventing
   * a heartbeat from being sent.
   * 
   * @param hostPort
   *          the host port
   */
  public abstract void reset(HostPort hostPort);

}