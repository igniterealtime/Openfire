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
package asia.stampy.client.message.nack;

import asia.stampy.client.message.AbstractClientMessageHeader;
import asia.stampy.common.StampyLibrary;

/**
 * The Class NackHeader.
 */
@StampyLibrary(libraryName="stampy-core")
public class NackHeader extends AbstractClientMessageHeader {
  private static final long serialVersionUID = -2432737523178348294L;

  /** The Constant TRANSACTION. */
  public static final String TRANSACTION = "transaction";

  /** The Constant ID. */
  public static final String ID = "id";

  /**
   * Sets the id.
   * 
   * @param id
   *          the new id
   */
  public void setId(String id) {
    addHeader(ID, id);
  }

  /**
   * Gets the id.
   * 
   * @return the id
   */
  public String getId() {
    return getHeaderValue(ID);
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
