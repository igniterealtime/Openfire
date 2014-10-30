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
 * Thrown when an instance of {@link SecurityMessageListener} has not been
 * configured first in the list.
 * 
 * @author burton
 * @see AbstractStampyMessageGateway#notifyMessageListeners(asia.stampy.common.message.StampyMessage,
 *      HostPort)
 * 
 */
@StampyLibrary(libraryName="stampy-client-server")
public class StampySecurityException extends RuntimeException {

  private static final long serialVersionUID = -9157111717358793044L;

  /**
   * Instantiates a new stampy security exception.
   */
  public StampySecurityException() {
    super(
        "An instance of SecurityMinaMessageListener has not been configured first in the list of StampyMinaMessageListeners");
  }

}
