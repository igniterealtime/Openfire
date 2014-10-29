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
package asia.stampy.server.message.receipt;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractMessageHeader;

/**
 * The Class ReceiptHeader.
 */
@StampyLibrary(libraryName="stampy-core")
public class ReceiptHeader extends AbstractMessageHeader {
  private static final long serialVersionUID = 2499933932635661316L;

  /** The Constant RECEIPT_ID. */
  public static final String RECEIPT_ID = "receipt-id";

  /**
   * Sets the receipt id.
   * 
   * @param receiptId
   *          the new receipt id
   */
  public void setReceiptId(String receiptId) {
    addHeader(RECEIPT_ID, receiptId);
  }

  /**
   * Gets the receipt id.
   * 
   * @return the receipt id
   */
  public String getReceiptId() {
    return getHeaderValue(RECEIPT_ID);
  }

}
