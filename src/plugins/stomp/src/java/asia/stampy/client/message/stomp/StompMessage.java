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
package asia.stampy.client.message.stomp;

import org.apache.commons.lang.StringUtils;

import asia.stampy.client.message.connect.ConnectHeader;
import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractMessage;
import asia.stampy.common.message.InvalidStompMessageException;
import asia.stampy.common.message.StompMessageType;

/**
 * The Class StompMessage.
 */
@StampyLibrary(libraryName="stampy-core")
public class StompMessage extends AbstractMessage<StompHeader> {

  private static final long serialVersionUID = 4889982516009469738L;

  /**
   * Instantiates a new stomp message.
   * 
   * @param host
   *          the host
   */
  public StompMessage(String host) {
    this();

    getHeader().setAcceptVersion("1.2");
    getHeader().setHost(host);
  }

  /**
   * Instantiates a new stomp message.
   */
  public StompMessage() {
    super(StompMessageType.STOMP);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#createNewHeader()
   */
  @Override
  protected StompHeader createNewHeader() {
    return new StompHeader();
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#validate()
   */
  @Override
  public void validate() {
    if (StringUtils.isEmpty(getHeader().getAcceptVersion())) {
      throw new InvalidStompMessageException(ConnectHeader.ACCEPT_VERSION + " is required, 1.2 only");
    }

    if (StringUtils.isEmpty(getHeader().getHost())) {
      throw new InvalidStompMessageException(ConnectHeader.HOST + " is required");
    }
  }

}
