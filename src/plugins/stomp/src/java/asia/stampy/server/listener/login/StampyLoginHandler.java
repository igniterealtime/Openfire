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
 * The Interface StampyLoginHandler.
 */
@StampyLibrary(libraryName="stampy-client-server")
public interface StampyLoginHandler {

  /**
   * Implementations are to perform any required login functionality. If the
   * login fails a {@link NotLoggedInException} is to be thrown. If the session
   * is to be terminated a {@link TerminateSessionException} is to be thrown.
   * 
   * @param username
   *          the username
   * @param password
   *          the password
   * @throws NotLoggedInException
   *           the not logged in exception
   * @throws TerminateSessionException
   *           the terminate session exception
   */
  void login(String username, String password) throws NotLoggedInException, TerminateSessionException;

}
