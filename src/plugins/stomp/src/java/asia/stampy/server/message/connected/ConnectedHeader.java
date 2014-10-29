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
package asia.stampy.server.message.connected;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractMessageHeader;

/**
 * The Class ConnectedHeader.
 */
@StampyLibrary(libraryName="stampy-core")
public class ConnectedHeader extends AbstractMessageHeader {

  private static final long serialVersionUID = 1548982417648641349L;

  /** The Constant SESSION. */
  public static final String SESSION = "session";

  /** The Constant SERVER. */
  public static final String SERVER = "server";

  /** The Constant HEART_BEAT. */
  public static final String HEART_BEAT = "heart-beat";

  /** The Constant VERSION. */
  public static final String VERSION = "version";

  /**
   * Sets the version.
   * 
   * @param version
   *          the new version
   */
  public void setVersion(String version) {
    addHeader(VERSION, version);
  }

  /**
   * Gets the version.
   * 
   * @return the version
   */
  public String getVersion() {
    return getHeaderValue(VERSION);
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
   * Gets the client heartbeat.
   * 
   * @return the client heartbeat
   */
  public int getOutgoingHeartbeat() {
    return getHeartbeat(0);
  }

  /**
   * Gets the server heartbeat.
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

  /**
   * Sets the server.
   * 
   * @param server
   *          the new server
   */
  public void setServer(String server) {
    addHeader(SERVER, server);
  }

  /**
   * Gets the server.
   * 
   * @return the server
   */
  public String getServer() {
    return getHeaderValue(SERVER);
  }

  /**
   * Sets the session.
   * 
   * @param session
   *          the new session
   */
  public void setSession(String session) {
    addHeader(SESSION, session);
  }

  /**
   * Gets the session.
   * 
   * @return the session
   */
  public String getSession() {
    return getHeaderValue(SESSION);
  }

}
