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
import asia.stampy.common.message.AbstractBodyMessage;
import asia.stampy.common.message.StompMessageType;

/**
 * The Class ErrorMessage.
 */
@StampyLibrary(libraryName="stampy-core")
public class ErrorMessage extends AbstractBodyMessage<ErrorHeader> {

  private static final long serialVersionUID = -4583369848020945035L;

  /**
   * Instantiates a new error message.
   */
  public ErrorMessage() {
    super(StompMessageType.ERROR);
  }

  /**
   * Instantiates a new error message.
   * 
   * @param receiptId
   *          the receipt id
   */
  public ErrorMessage(String receiptId) {
    this();

    getHeader().setReceiptId(receiptId);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#createNewHeader()
   */
  @Override
  protected ErrorHeader createNewHeader() {
    return new ErrorHeader();
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#validate()
   */
  @Override
  public void validate() {
    // TODO Auto-generated method stub

  }

}
