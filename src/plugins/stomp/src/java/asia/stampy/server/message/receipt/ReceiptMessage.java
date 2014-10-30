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

import org.apache.commons.lang.StringUtils;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractMessage;
import asia.stampy.common.message.InvalidStompMessageException;
import asia.stampy.common.message.StompMessageType;

/**
 * The Class ReceiptMessage.
 */
@StampyLibrary(libraryName="stampy-core")
public class ReceiptMessage extends AbstractMessage<ReceiptHeader> {

  private static final long serialVersionUID = -5942932500390572224L;

  /**
   * Instantiates a new receipt message.
   * 
   * @param receiptId
   *          the receipt id
   */
  public ReceiptMessage(String receiptId) {
    this();

    getHeader().setReceiptId(receiptId);
  }

  /**
   * Instantiates a new receipt message.
   */
  public ReceiptMessage() {
    super(StompMessageType.RECEIPT);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#createNewHeader()
   */
  @Override
  protected ReceiptHeader createNewHeader() {
    return new ReceiptHeader();
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#validate()
   */
  @Override
  public void validate() {
    if (StringUtils.isEmpty(getHeader().getReceiptId())) {
      throw new InvalidStompMessageException(ReceiptHeader.RECEIPT_ID + " is required");
    }
  }

}
