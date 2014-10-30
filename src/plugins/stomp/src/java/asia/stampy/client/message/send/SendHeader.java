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
package asia.stampy.client.message.send;

import asia.stampy.client.message.AbstractClientBodyMessageHeader;
import asia.stampy.common.StampyLibrary;

/**
 * The Class SendHeader.
 */
@StampyLibrary(libraryName="stampy-core")
public class SendHeader extends AbstractClientBodyMessageHeader {

  private static final long serialVersionUID = -4105777135779226205L;

  /** The Constant TRANSACTION. */
  public static final String TRANSACTION = "transaction";

  /** The Constant DESTINATION. */
  public static final String DESTINATION = "destination";

  /**
   * Sets the destination.
   * 
   * @param destination
   *          the new destination
   */
  public void setDestination(String destination) {
    addHeader(DESTINATION, destination);
  }

  /**
   * Gets the destination.
   * 
   * @return the destination
   */
  public String getDestination() {
    return getHeaderValue(DESTINATION);
  }

  /**
   * Sets the transaction.
   * 
   * @param transaction
   *          the new transaction
   */
  public void setTransaction(String transaction) {
    addHeader(TRANSACTION, transaction);
  }

  /**
   * Gets the transaction.
   * 
   * @return the transaction
   */
  public String getTransaction() {
    return getHeaderValue(TRANSACTION);
  }
}
