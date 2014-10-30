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
package asia.stampy.server.message.message;

import org.apache.commons.lang.StringUtils;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractBodyMessage;
import asia.stampy.common.message.InvalidStompMessageException;
import asia.stampy.common.message.StompMessageType;

/**
 * The Class MessageMessage.
 */
@StampyLibrary(libraryName="stampy-core")
public class MessageMessage extends AbstractBodyMessage<MessageHeader> {

  private static final long serialVersionUID = 5351072786156865214L;

  /**
   * Instantiates a new message message.
   * 
   * @param destination
   *          the destination
   * @param messageId
   *          the message id
   * @param subscription
   *          the subscription
   */
  public MessageMessage(String destination, String messageId, String subscription) {
    this();

    getHeader().setDestination(destination);
    getHeader().setMessageId(messageId);
    getHeader().setSubscription(subscription);
  }

  /**
   * Instantiates a new message message.
   */
  public MessageMessage() {
    super(StompMessageType.MESSAGE);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#createNewHeader()
   */
  @Override
  protected MessageHeader createNewHeader() {
    return new MessageHeader();
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#validate()
   */
  @Override
  public void validate() {
    if (StringUtils.isEmpty(getHeader().getDestination())) {
      throw new InvalidStompMessageException(MessageHeader.DESTINATION + " is required");
    }

    if (StringUtils.isEmpty(getHeader().getMessageId())) {
      throw new InvalidStompMessageException(MessageHeader.MESSAGE_ID + " is required");
    }

    if (StringUtils.isEmpty(getHeader().getSubscription())) {
      throw new InvalidStompMessageException(MessageHeader.SUBSCRIPTION + " is required");
    }
  }

}
