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
package asia.stampy.server.listener.subscription;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.StompMessageType;

/**
 * The Interface StampyAcknowledgementHandler.
 * 
 * @see AbstractAcknowledgementListenerAndInterceptor#setHandler(StampyAcknowledgementHandler)
 */
@StampyLibrary(libraryName = "stampy-client-server")
public interface StampyAcknowledgementHandler {

  /**
   * Invoked when an {@link StompMessageType#ACK} message has been received for
   * a published {@link StompMessageType#MESSAGE}.
   * 
   * @param id
   *          the id
   * @param receipt
   *          the receipt
   * @param transaction
   *          the transaction
   * @throws Exception
   *           the exception
   */
  void ackReceived(String id, String receipt, String transaction) throws Exception;

  /**
   * Invoked when an {@link StompMessageType#NACK} message has been received for
   * a missing {@link StompMessageType#MESSAGE}.
   * 
   * @param id
   *          the id
   * @param receipt
   *          the receipt
   * @param transaction
   *          the transaction
   * @throws Exception
   *           the exception
   */
  void nackReceived(String id, String receipt, String transaction) throws Exception;

  /**
   * Invoked when the acknowledgement timer has finished and no acknowledgement
   * has been received.
   * 
   * @param id
   *          the id
   */
  void noAcknowledgementReceived(String id);

}
