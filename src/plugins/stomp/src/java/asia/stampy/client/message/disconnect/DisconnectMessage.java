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
package asia.stampy.client.message.disconnect;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractMessage;
import asia.stampy.common.message.StompMessageType;

/**
 * The Class DisconnectMessage.
 */
@StampyLibrary(libraryName="stampy-core")
public class DisconnectMessage extends AbstractMessage<DisconnectHeader> {

  private static final long serialVersionUID = -7353329342186049989L;

  /**
   * Instantiates a new disconnect message.
   */
  public DisconnectMessage() {
    super(StompMessageType.DISCONNECT);
    getHeader();
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#createNewHeader()
   */
  @Override
  protected DisconnectHeader createNewHeader() {
    return new DisconnectHeader();
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#validate()
   */
  @Override
  public void validate() {
    // TODO Auto-generated method stub

  }

}
