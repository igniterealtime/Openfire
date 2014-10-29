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
package asia.stampy.common.message;

import asia.stampy.common.StampyLibrary;

/**
 * The Enum of all STOMP message types.
 */
@StampyLibrary(libraryName="stampy-core")
public enum StompMessageType {

  /** The connect. */
  CONNECT,

  /** The stomp. */
  STOMP,

  /** The connected. */
  CONNECTED,

  /** The send. */
  SEND(true),

  /** The subscribe. */
  SUBSCRIBE,

  /** The unsubscribe. */
  UNSUBSCRIBE,

  /** The ack. */
  ACK,

  /** The nack. */
  NACK,

  /** The begin. */
  BEGIN,

  /** The commit. */
  COMMIT,

  /** The abort. */
  ABORT,

  /** The disconnect. */
  DISCONNECT,

  /** The message. */
  MESSAGE(true),

  /** The receipt. */
  RECEIPT,

  /** The error. */
  ERROR(true);

  /** The has body. */
  boolean hasBody;

  /**
   * Instantiates a new stampy message type.
   */
  StompMessageType() {
    this(false);
  }

  /**
   * Instantiates a new stampy message type.
   * 
   * @param hasBody
   *          the has body
   */
  StompMessageType(boolean hasBody) {
    this.hasBody = hasBody;
  }

  /**
   * Checks for body.
   * 
   * @return true, if successful
   */
  public boolean hasBody() {
    return hasBody;
  }

}
