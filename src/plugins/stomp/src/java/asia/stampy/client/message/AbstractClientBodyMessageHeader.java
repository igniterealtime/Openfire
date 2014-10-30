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
package asia.stampy.client.message;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractBodyMessageHeader;

/**
 * The Class AbstractClientBodyMessageHeader.
 */
@StampyLibrary(libraryName="stampy-core")
public class AbstractClientBodyMessageHeader extends AbstractBodyMessageHeader implements ClientMessageHeader {
  private static final long serialVersionUID = -4466902797463186691L;

  /**
   * Sets the receipt.
   * 
   * @param receipt
   *          the new receipt
   */
  public void setReceipt(String receipt) {
    addHeader(RECEIPT, receipt);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.client.message.ClientMessageHeader#getReceipt()
   */
  @Override
  public String getReceipt() {
    return getHeaderValue(RECEIPT);
  }

}
