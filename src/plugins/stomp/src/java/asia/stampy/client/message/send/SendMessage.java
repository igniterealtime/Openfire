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

import org.apache.commons.lang.StringUtils;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractBodyMessage;
import asia.stampy.common.message.InvalidStompMessageException;
import asia.stampy.common.message.StompMessageType;

/**
 * The Class SendMessage.
 */
@StampyLibrary(libraryName="stampy-core")
public class SendMessage extends AbstractBodyMessage<SendHeader> {

  private static final long serialVersionUID = -104251665180607773L;

  /**
   * Instantiates a new send message.
   * 
   * @param destination
   *          the destination
   * @param receipt
   *          the receipt
   */
  public SendMessage(String destination, String receipt) {
    this();

    getHeader().setDestination(destination);
    getHeader().setReceipt(receipt);
  }

  /**
   * Instantiates a new send message.
   */
  public SendMessage() {
    super(StompMessageType.SEND);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#createNewHeader()
   */
  @Override
  protected SendHeader createNewHeader() {
    return new SendHeader();
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#validate()
   */
  @Override
  public void validate() {
    if (StringUtils.isEmpty(getHeader().getDestination())) {
      throw new InvalidStompMessageException(SendHeader.DESTINATION + " is required");
    }
  }

}
