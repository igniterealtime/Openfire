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

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.serialization.SerializationUtils;

/**
 * Abstract class representing STOMP messages with a body. Binary bodies are
 * catered for, provided they are Java objects.
 * 
 * @param <HDR>
 *          the generic type
 */
@StampyLibrary(libraryName="stampy-core")
public abstract class AbstractBodyMessage<HDR extends AbstractBodyMessageHeader> extends AbstractMessage<HDR> {
  private static final long serialVersionUID = 3988865546656906553L;

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** The Constant JAVA_BASE64_MIME_TYPE. */
  public static final String JAVA_BASE64_MIME_TYPE = "java/base64";

  private String bodyEncoding = JAVA_BASE64_MIME_TYPE;

  private Object body;

  /**
   * Instantiates a new abstract body message.
   * 
   * @param messageType
   *          the message type
   */
  protected AbstractBodyMessage(StompMessageType messageType) {
    super(messageType);
  }

  /**
   * Gets the body.
   * 
   * @param <O>
   *          the generic type
   * @return the body
   */
  @SuppressWarnings("unchecked")
  public <O extends Object> O getBody() {
    return (O) body;
  }

  /**
   * Sets the body.
   * 
   * @param <O>
   *          the generic type
   * @param body
   *          the new body
   */
  public <O extends Object> void setBody(O body) {
    this.body = body;
  }

  /**
   * Sets the mime type.
   * 
   * @param mimeType
   *          the new mime type
   */
  public void setMimeType(String mimeType) {
    getHeader().setContentType(mimeType);
  }

  /**
   * Sets the mime type.
   * 
   * @param mimeType
   *          the mime type
   * @param encoding
   *          the encoding
   */
  public void setMimeType(String mimeType, String encoding) {
    mimeType += ";charset=" + encoding;
    setMimeType(mimeType);
  }

  /**
   * Checks if is text.
   * 
   * @return true, if is text
   */
  public boolean isText() {
    String value = getHeader().getContentType();
    if (value == null) return true;

    return value.contains("text/");
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.message.AbstractMessage#postHeader()
   */
  @Override
  protected String postHeader() {
    if (getBody() == null) return null;

    if (isText()) {
      return getBody();
    } else {
      try {
        String encoded = getBodyEncoding().equals(JAVA_BASE64_MIME_TYPE) ? getObjectArrayAsBase64(getBody())
            : getObjectArrayAsString(getBody());
        getHeader().removeHeader(AbstractBodyMessageHeader.CONTENT_TYPE);
        getHeader().removeHeader(AbstractMessageHeader.CONTENT_LENGTH);
        getHeader().setContentLength(encoded.length());
        getHeader().setContentType(getBodyEncoding());
        return encoded;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Should the {@link AbstractBodyMessage#getBodyEncoding()} return a value
   * other than {@link AbstractBodyMessage#JAVA_BASE64_MIME_TYPE}, this method
   * will be invoked. The default implementation throws a
   * NotImplementedException. Override as necessary.
   * 
   * @param body
   *          the body
   * @return the object array as string
   */
  protected String getObjectArrayAsString(Object body) {
    throw new NotImplementedException("Subclass the abstract body message and override getObjectArrayAsString for "
        + getBodyEncoding() + " encoding");
  }

  /**
   * Gets the object array as base64.
   * 
   * @param o
   *          the o
   * @return the object array as base64
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  public String getObjectArrayAsBase64(Object o) throws IOException {
    log.debug("Serializing object to a string using Base64 encoding", o);
    return SerializationUtils.serializeBase64(o);
  }

  /**
   * Gets the body encoding. Defaults to
   * {@link AbstractBodyMessage#JAVA_BASE64_MIME_TYPE}.
   * 
   * @return the body encoding
   */
  public String getBodyEncoding() {
    return bodyEncoding;
  }

  /**
   * Sets the body encoding.
   * 
   * @param bodyEncoding
   *          the new body encoding
   */
  public void setBodyEncoding(String bodyEncoding) {
    this.bodyEncoding = bodyEncoding;
  }

}
