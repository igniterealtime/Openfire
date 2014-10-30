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
package asia.stampy.client.message.connect;

import org.apache.commons.lang.StringUtils;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractMessage;
import asia.stampy.common.message.InvalidStompMessageException;
import asia.stampy.common.message.StompMessageType;

/**
 * The Class ConnectMessage.
 */
@StampyLibrary(libraryName="stampy-core")
public class ConnectMessage extends AbstractMessage<ConnectHeader> {

  private static final long serialVersionUID = 1164477258648698915L;

  /**
   * Instantiates a new connect message.
   * 
   * @param acceptVersion
   *          the accept version
   * @param host
   *          the host
   */
  public ConnectMessage(String acceptVersion, String host) {
    this();

    getHeader().setAcceptVersion(acceptVersion);
    getHeader().setHost(host);
  }

  /**
   * Instantiates a new connect message.
   * 
   * @param host
   *          the host
   */
  public ConnectMessage(String host) {
    this("1.2", host);
  }

  /**
   * Instantiates a new connect message.
   */
  public ConnectMessage() {
    super(StompMessageType.CONNECT);
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#createNewHeader()
   */
  @Override
  protected ConnectHeader createNewHeader() {
    return new ConnectHeader();
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
