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

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractBodyMessageHeader;

/**
 * The Class MessageHeader.
 */
@StampyLibrary(libraryName="stampy-core")
public class MessageHeader extends AbstractBodyMessageHeader {

  private static final long serialVersionUID = -1715758376656092863L;

  /** The Constant ACK. */
  public static final String ACK = "ack";

  /** The Constant SUBSCRIPTION. */
  public static final String SUBSCRIPTION = "subscription";

  /** The Constant MESSAGE_ID. */
  public static final String MESSAGE_ID = "message-id";

  /** The Constant DESTINATION. */
  public static final String DESTINATION = "destination";

  /**
   * Sets the destination.
   * 
   * @param destination
   *          the new destination
   */
  public void setDestination(String destination) {
    addHeader(DESTINATION, destination);
  }

  /**
   * Gets the destination.
   * 
   * @return the destination
   */
  public String getDestination() {
    return getHeaderValue(DESTINATION);
  }

  /**
   * Sets the message id.
   * 
   * @param messageId
   *          the new message id
   */
  public void setMessageId(String messageId) {
    addHeader(MESSAGE_ID, messageId);
  }

  /**
   * Gets the message id.
   * 
   * @return the message id
   */
  public String getMessageId() {
    return getHeaderValue(MESSAGE_ID);
  }

  /**
   * Sets the subscription.
   * 
   * @param subscription
   *          the new subscription
   */
  public void setSubscription(String subscription) {
    addHeader(SUBSCRIPTION, subscription);
  }

  /**
   * Gets the subscription.
   * 
   * @return the subscription
   */
  public String getSubscription() {
    return getHeaderValue(SUBSCRIPTION);
  }

  /**
   * Sets the ack.
   */
  public void setAck(String ack) {
    addHeader(ACK, ack);
  }

  /**
   * Gets the ack.
   * 
   * @return the ack
   */
  public String getAck() {
    return getHeaderValue(ACK);
  }
}
