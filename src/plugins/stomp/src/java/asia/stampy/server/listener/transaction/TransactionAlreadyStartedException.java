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
package asia.stampy.server.listener.transaction;

import asia.stampy.client.message.begin.BeginMessage;
import asia.stampy.common.StampyLibrary;

/**
 * The Class TransactionAlreadyStartedException is thrown by
 * {@link AbstractTransactionListener} when a named transaction has already been
 * started and an additional {@link BeginMessage} has been received for that
 * transaction.
 * 
 * @see AbstractTransactionListener
 */
@StampyLibrary(libraryName = "stampy-client-server")
public class TransactionAlreadyStartedException extends Exception {

  private static final long serialVersionUID = 4327171405667138826L;

  /**
   * Instantiates a new transaction already started exception.
   * 
   * @param message
   *          the message
   */
  public TransactionAlreadyStartedException(String message) {
    super(message);
  }

}
