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
package asia.stampy.client.message.subscribe;

import asia.stampy.client.message.AbstractClientMessageHeader;
import asia.stampy.common.StampyLibrary;

/**
 * The Class SubscribeHeader.
 */
@StampyLibrary(libraryName="stampy-core")
public class SubscribeHeader extends AbstractClientMessageHeader {
  private static final long serialVersionUID = 2321658220170938363L;

  /** The Constant ID. */
  public static final String ID = "id";

  /** The Constant ACK. */
  public static final String ACK = "ack";

  /** The Constant DESTINATION. */
  public static final String DESTINATION = "destination";

  /**
   * The Enum Ack.
   */
  public enum Ack {

    /** The auto. */
    auto("auto"),

    /** The client. */
    client("client"),

    /** The client individual. */
    clientIndividual("client-individual");

    /** The ack value. */
    String ackValue;

    /**
     * Instantiates a new ack.
     * 
     * @param ackValue
     *          the ack value
     */
    Ack(String ackValue) {
      this.ackValue = ackValue;
    }

    /**
     * Gets the ack value.
     * 
     * @return the ack value
     */
    public String getAckValue() {
      return ackValue;
    }

    /**
     * From string.
     * 
     * @param s
     *          the s
     * @return the ack
     */
    public static Ack fromString(String s) {
      for (Ack ack : Ack.values()) {
        if (ack.getAckValue().equals(s)) {
          return ack;
        }
      }

      return null;
    }
  }

  /**
   * Sets the destination.
   * 
   * @param destination
   *          the new destination
   */
  public void setDestination(String destination) {
    addHeader(DESTINATION, destination);
  }

  /**
   * Gets the destination.
   * 
   * @return the destination
   */
  public String getDestination() {
    return getHeaderValue(DESTINATION);
  }

  /**
   * Sets the ack.
   * 
   * @param ack
   *          the new ack
   */
  public void setAck(Ack ack) {
    addHeader(ACK, ack.getAckValue());
  }

  /**
   * Gets the ack.
   * 
   * @return the ack
   */
  public Ack getAck() {
    String s = getHeaderValue(ACK);
    if (s == null) return null;

    return Ack.fromString(s);
  }

  /**
   * Sets the id.
   * 
   * @param id
   *          the new id
   */
  public void setId(String id) {
    addHeader(ID, id);
  }

  /**
   * Gets the id.
   * 
   * @return the id
   */
  public String getId() {
    return getHeaderValue(ID);
  }

}
