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
package asia.stampy.server.listener.login;

import asia.stampy.common.StampyLibrary;

/**
 * The Class NotLoggedInException indicates that the {@link StampyLoginHandler} implementation
 * failed the login credentials for the specified reason.
 * 
 * @see StampyLoginHandler
 * @see AbstractLoginMessageListener
 */
@StampyLibrary(libraryName="stampy-client-server")
public class NotLoggedInException extends Exception {

  private static final long serialVersionUID = 6115947456180110688L;

  /**
   * Instantiates a new not logged in exception.
   * 
   * @param message
   *          the message
   */
  public NotLoggedInException(String message) {
    super(message);
  }

}
