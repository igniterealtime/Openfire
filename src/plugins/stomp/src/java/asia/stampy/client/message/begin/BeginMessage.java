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
package asia.stampy.client.message.begin;

import org.apache.commons.lang.StringUtils;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractMessage;
import asia.stampy.common.message.InvalidStompMessageException;
import asia.stampy.common.message.StompMessageType;

/**
 * The Class BeginMessage.
 */
@StampyLibrary(libraryName="stampy-core")
public class BeginMessage extends AbstractMessage<BeginHeader> {

  private static final long serialVersionUID = -1331929421949592240L;

  /**
   * Instantiates a new begin message.
   * 
   * @param transaction
   *          the transaction
   */
  public BeginMessage(String transaction) {
    this();

    getHeader().setTransaction(transaction);
  }

  /**
   * Instantiates a new begin message.
   */
  public BeginMessage() {
    super(StompMessageType.BEGIN);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#createNewHeader()
   */
  @Override
  protected BeginHeader createNewHeader() {
    return new BeginHeader();
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#validate()
   */
  @Override
  public void validate() {
    if (StringUtils.isEmpty(getHeader().getTransaction())) {
      throw new InvalidStompMessageException(BeginHeader.TRANSACTION + " is required");
    }
  }

}
