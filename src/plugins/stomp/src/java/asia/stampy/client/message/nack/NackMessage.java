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
package asia.stampy.client.message.nack;

import org.apache.commons.lang.StringUtils;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractMessage;
import asia.stampy.common.message.InvalidStompMessageException;
import asia.stampy.common.message.StompMessageType;

/**
 * The Class NackMessage.
 */
@StampyLibrary(libraryName="stampy-core")
public class NackMessage extends AbstractMessage<NackHeader> {

  private static final long serialVersionUID = -1213685585376641464L;

  /**
   * Instantiates a new nack message.
   * 
   * @param id
   *          the id
   */
  public NackMessage(String id) {
    this();

    getHeader().setId(id);
  }

  /**
   * Instantiates a new nack message.
   */
  public NackMessage() {
    super(StompMessageType.NACK);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#createNewHeader()
   */
  @Override
  protected NackHeader createNewHeader() {
    return new NackHeader();
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#validate()
   */
  @Override
  public void validate() {
    if (StringUtils.isEmpty(getHeader().getId())) {
      throw new InvalidStompMessageException(NackHeader.ID + " is required");
    }
  }

}
