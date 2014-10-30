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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;

/**
 * This default implementation of an {@link UnparseableMessageHandler} logs a
 * non-STOMP message at warning level.
 */
@StampyLibrary(libraryName="stampy-client-server")
public class DefaultUnparseableMessageHandler implements UnparseableMessageHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /*
   * (non-Javadoc)
   * 
   * @see
   * asia.stampy.common.gateway.UnparseableMessageHandler#unparseableMessage
   * (java .lang.String, asia.stampy.common.HostPort)
   */
  @Override
  public void unparseableMessage(String message, HostPort hostPort) throws Exception {
    log.warn("Unparseable message {} received from {}", message, hostPort);
  }

}
