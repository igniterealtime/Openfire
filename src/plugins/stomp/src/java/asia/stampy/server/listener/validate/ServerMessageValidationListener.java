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
package asia.stampy.server.listener.validate;

import javax.annotation.Resource;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;

/**
 * Ensures that only client messages are accepted on the client. The validate()
 * method on the message is invoked.
 */
@Resource
@StampyLibrary(libraryName = "stampy-client-server")
public class ServerMessageValidationListener implements StampyMessageListener {

  private static StompMessageType[] TYPES = StompMessageType.values();

  @Override
  public StompMessageType[] getMessageTypes() {
    return TYPES;
  }

  @Override
  public boolean isForMessage(StampyMessage<?> message) {
    return true;
  }

  @Override
  public void messageReceived(StampyMessage<?> message, HostPort hostPort) throws Exception {
    switch (message.getMessageType()) {

    case ABORT:
    case ACK:
    case BEGIN:
    case COMMIT:
    case CONNECT:
    case STOMP:
    case DISCONNECT:
    case NACK:
    case SEND:
    case SUBSCRIBE:
    case UNSUBSCRIBE:
      message.validate();
      break;
    default:
      throw new IllegalArgumentException(message.getMessageType() + " is not a valid STOMP 1.2 client message");

    }
  }

}
