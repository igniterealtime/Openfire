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
package asia.stampy.common.message;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.parsing.StompMessageParser;

/**
 * Abstract implementation of a {@link StampyMessage}.
 * 
 * @param <HDR>
 *          the generic type
 */
@StampyLibrary(libraryName="stampy-core")
public abstract class AbstractMessage<HDR extends StampyMessageHeader> implements StampyMessage<HDR> {

  private static final long serialVersionUID = -577180637937320507L;

  private HDR header;
  private final StompMessageType messageType;

  /**
   * Instantiates a new abstract message.
   * 
   * @param messageType
   *          the message type
   */
  protected AbstractMessage(StompMessageType messageType) {
    this.messageType = messageType;
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.StampyMessage#getHeader()
   */
  @Override
  public HDR getHeader() {
    if (header == null) header = createNewHeader();
    return header;
  }

  /**
   * Creates the new header.
   * 
   * @return the hdr
   */
  protected abstract HDR createNewHeader();

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.StampyMessage#getMessageType()
   */
  @Override
  public StompMessageType getMessageType() {
    return messageType;
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.StampyMessage#toStompMessage(boolean)
   */
  @Override
  public final String toStompMessage(boolean validate) {
    if (validate) validate();

    StringBuilder builder = new StringBuilder();

    String body = postHeader();

    builder.append(getMessageType().name());
    String header = getHeader().toMessageHeader();
    if (StringUtils.isNotEmpty(header)) {
      builder.append("\n");
      builder.append(header);
    }
    builder.append("\n\n");
    builder.append(body);

    builder.append(StompMessageParser.EOM);

    return builder.toString();
  }

  /**
   * Validates the message should {@link AbstractMessage#toStompMessage(true)}
   * be called.
   */
  @Override
  public abstract void validate();

  /**
   * This method is used to create the body of the message, if applicable. The
   * default implementation returns null.
   * 
   * @return the string
   */
  protected String postHeader() {
    return null;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }

}
