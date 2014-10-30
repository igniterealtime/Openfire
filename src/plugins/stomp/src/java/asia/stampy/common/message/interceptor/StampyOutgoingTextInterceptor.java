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
package asia.stampy.common.message.interceptor;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import asia.stampy.common.gateway.HostPort;

/**
 * This interface is implemented to intercept specified messages to capture the
 * state of any outgoing messages.
 * 
 * @author burton
 * 
 * @see AbstractStampyMessageGateway
 */
@StampyLibrary(libraryName="stampy-client-server")
public interface StampyOutgoingTextInterceptor {

  /**
   * Intercepts the outgoing message for capturing state etc.
   * 
   * @param message
   *          the message
   * @throws InterceptException
   *           if the outgoing message is to be aborted
   */
  void interceptMessage(String message) throws InterceptException;

  /**
   * Intercepts the outgoing message for capturing state etc, invoked by the.
   * 
   * @param message
   *          the message
   * @param hostPort
   *          the host port
   * @throws InterceptException
   *           if the outgoing message is to be aborted
   *           {@link AbstractStampyMessageGateway}
   * @see AbstractStampyMessageGateway
   */
  void interceptMessage(String message, HostPort hostPort) throws InterceptException;

}
