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

/**
 * <i>any client frame other than CONNECT MAY specify a receipt header with an
 * arbitrary value. This will cause the server to acknowledge the processing of
 * the client frame with a RECEIPT frame</i>
 */
@StampyLibrary(libraryName="stampy-core")
public interface ClientMessageHeader {

  /** The Constant RECEIPT. */
  public static final String RECEIPT = "receipt";

  /**
   * Gets the receipt.
   * 
   * @return the receipt
   */
  String getReceipt();

}
