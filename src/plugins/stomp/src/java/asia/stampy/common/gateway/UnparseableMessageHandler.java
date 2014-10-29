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

import asia.stampy.common.StampyLibrary;

/**
 * Implementations deal with messages that cannot be parsed into STOMP messages.
 */
@StampyLibrary(libraryName="stampy-client-server")
public interface UnparseableMessageHandler {

  /**
   * Process the non-STOMP message.
   * 
   * @param message
   *          the message
   * @param hostPort
   *          the host port
   * @throws Exception
   *           the exception
   */
  void unparseableMessage(String message, HostPort hostPort) throws Exception;

}
