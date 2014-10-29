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

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractMessageHeader;

/**
 * The Class ConnectHeader.
 */
@StampyLibrary(libraryName="stampy-core")
public class ConnectHeader extends AbstractMessageHeader {

  private static final long serialVersionUID = 6732326426523759109L;

  /** The Constant HEART_BEAT. */
  public static final String HEART_BEAT = "heart-beat";

  /** The Constant PASSCODE. */
  public static final String PASSCODE = "passcode";

  /** The Constant LOGIN. */
  public static final String LOGIN = "login";

  /** The Constant HOST. */
  public static final String HOST = "host";

  /** The Constant ACCEPT_VERSION. */
  public static final String ACCEPT_VERSION = "accept-version";

  /**
   * Sets the accept version.
   * 
   * @param acceptVersion
   *          the new accept version
   */
  public void setAcceptVersion(String acceptVersion) {
    addHeader(ACCEPT_VERSION, acceptVersion);
  }

  /**
   * Gets the accept version.
   * 
   * @return the accept version
   */
  public String getAcceptVersion() {
    return getHeaderValue(ACCEPT_VERSION);
  }

  /**
   * Sets the host.
   * 
   * @param host
   *          the new host
   */
  public void setHost(String host) {
    addHeader(HOST, host);
  }

  /**
   * Gets the host.
   * 
   * @return the host
   */
  public String getHost() {
    return getHeaderValue(HOST);
  }

  /**
   * Sets the login.
   * 
   * @param user
   *          the new login
   */
  public void setLogin(String user) {
    addHeader(LOGIN, user);
  }

  /**
   * Gets the login.
   * 
   * @return the login
   */
  public String getLogin() {
    return getHeaderValue(LOGIN);
  }

  /**
   * Sets the passcode.
   * 
   * @param passcode
   *          the new passcode
   */
  public void setPasscode(String passcode) {
    addHeader(PASSCODE, passcode);
  }

  /**
   * Gets the passcode.
   * 
   * @return the passcode
   */
  public String getPasscode() {
    return getHeaderValue(PASSCODE);
  }

  /**
   * Sets the heartbeat.
   * 
   * @param outgoingHeartbeat
   *          the client hb millis
   * @param incomingHeartbeat
   *          the server hb millis
   */
  public void setHeartbeat(int outgoingHeartbeat, int incomingHeartbeat) {
    addHeader(HEART_BEAT, Integer.toString(outgoingHeartbeat) + "," + Integer.toString(incomingHeartbeat));
  }

  /**
   * Gets the outgoing heartbeat sleep time requested in milliseconds.
   * 
   * @return the client heartbeat
   */
  public int getOutgoingHeartbeat() {
    return getHeartbeat(0);
  }

  /**
   * Gets the incoming heartbeat sleep time requested in milliseconds.
   * 
   * @return the server heartbeat
   */
  public int getIncomingHeartbeat() {
    return getHeartbeat(1);
  }

  private int getHeartbeat(int pos) {
    String hb = getHeaderValue(HEART_BEAT);
    if (hb == null) return 0;

    String[] parts = hb.split(",");

    return Integer.parseInt(parts[pos]);
  }
}
