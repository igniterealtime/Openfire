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

import asia.stampy.client.message.abort.AbortMessage;
import asia.stampy.client.message.begin.BeginMessage;
import asia.stampy.client.message.commit.CommitMessage;
import asia.stampy.common.StampyLibrary;

/**
 * The Class TransactionNotStartedException is thrown when a
 * {@link CommitMessage} or {@link AbortMessage} is received and no
 * {@link BeginMessage} has started the transaction.
 * 
 * @see AbstractTransactionListener
 */
@StampyLibrary(libraryName = "stampy-client-server")
public class TransactionNotStartedException extends Exception {

  private static final long serialVersionUID = -651656641322030058L;

  /**
   * Instantiates a new transaction not started exception.
   * 
   * @param message
   *          the message
   */
  public TransactionNotStartedException(String message) {
    super(message);
  }

}
