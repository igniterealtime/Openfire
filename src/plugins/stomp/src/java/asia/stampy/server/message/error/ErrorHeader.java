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
package asia.stampy.server.message.error;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractBodyMessageHeader;

/**
 * The Class ErrorHeader.
 */
@StampyLibrary(libraryName="stampy-core")
public class ErrorHeader extends AbstractBodyMessageHeader {

  private static final long serialVersionUID = -4679565144569363907L;

  /** The Constant MESSAGE. */
  public static final String MESSAGE = "message";

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

  /**
   * Sets the message header.
   * 
   * @param shortMessage
   *          the new message header
   */
  public void setMessageHeader(String shortMessage) {
    addHeader(MESSAGE, shortMessage);
  }

  /**
   * Gets the message header.
   * 
   * @return the message header
   */
  public String getMessageHeader() {
    return getHeaderValue(MESSAGE);
  }

}
