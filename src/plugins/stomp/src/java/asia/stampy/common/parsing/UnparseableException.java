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
package asia.stampy.common.parsing;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.StampyMessage;

/**
 * Thrown when a presumed STOMP message cannot be parsed to a
 * {@link StampyMessage}.
 */
@StampyLibrary(libraryName="stampy-core")
public class UnparseableException extends Exception {
  private static final long serialVersionUID = -5077635019985663697L;

  private String stompMessage;

  /**
   * Instantiates a new unparseable exception.
   * 
   * @param message
   *          the message
   * @param stompMessage
   *          the stomp message
   * @param cause
   *          the cause
   */
  public UnparseableException(String message, String stompMessage, Throwable cause) {
    super(message, cause);
    this.stompMessage = stompMessage;
  }

  /**
   * Instantiates a new unparseable exception.
   * 
   * @param message
   *          the message
   */
  public UnparseableException(String message) {
    super(message);
  }

  /**
   * Gets the stomp message.
   * 
   * @return the stomp message
   */
  public String getStompMessage() {
    return stompMessage;
  }

}
