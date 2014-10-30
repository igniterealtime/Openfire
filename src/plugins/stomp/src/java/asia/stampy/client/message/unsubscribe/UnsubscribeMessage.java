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
package asia.stampy.client.message.unsubscribe;

import org.apache.commons.lang.StringUtils;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractMessage;
import asia.stampy.common.message.InvalidStompMessageException;
import asia.stampy.common.message.StompMessageType;

/**
 * The Class UnsubscribeMessage.
 */
@StampyLibrary(libraryName="stampy-core")
public class UnsubscribeMessage extends AbstractMessage<UnsubscribeHeader> {

  private static final long serialVersionUID = -4889351559871669847L;

  /**
   * Instantiates a new unsubscribe message.
   * 
   * @param id
   *          the id
   */
  public UnsubscribeMessage(String id) {
    this();

    getHeader().setId(id);
  }

  /**
   * Instantiates a new unsubscribe message.
   */
  public UnsubscribeMessage() {
    super(StompMessageType.UNSUBSCRIBE);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#createNewHeader()
   */
  @Override
  protected UnsubscribeHeader createNewHeader() {
    return new UnsubscribeHeader();
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#validate()
   */
  @Override
  public void validate() {
    if (StringUtils.isEmpty(getHeader().getId())) {
      throw new InvalidStompMessageException(UnsubscribeHeader.ID + " is required");
    }
  }

}
