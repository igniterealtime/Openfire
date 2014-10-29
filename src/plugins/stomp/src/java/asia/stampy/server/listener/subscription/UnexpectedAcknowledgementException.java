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
package asia.stampy.server.listener.subscription;

import asia.stampy.client.message.ack.AckMessage;
import asia.stampy.client.message.nack.NackMessage;
import asia.stampy.common.StampyLibrary;

/**
 * The Class UnexpectedAcknowledgementException indicates that an unexpected
 * {@link AckMessage} or {@link NackMessage} has been received by the
 * {@link AbstractAcknowledgementListenerAndInterceptor}
 * 
 * @see AbstractAcknowledgementListenerAndInterceptor
 */
@StampyLibrary(libraryName = "stampy-client-server")
public class UnexpectedAcknowledgementException extends Exception {

  private static final long serialVersionUID = 9160361992156988284L;

  /**
   * Instantiates a new unexpected acknowledgement exception.
   * 
   * @param message
   *          the message
   */
  public UnexpectedAcknowledgementException(String message) {
    super(message);
  }

}
