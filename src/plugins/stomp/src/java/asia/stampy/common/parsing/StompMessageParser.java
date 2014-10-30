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
package asia.stampy.common.parsing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.client.message.abort.AbortMessage;
import asia.stampy.client.message.ack.AckMessage;
import asia.stampy.client.message.begin.BeginMessage;
import asia.stampy.client.message.commit.CommitMessage;
import asia.stampy.client.message.connect.ConnectMessage;
import asia.stampy.client.message.disconnect.DisconnectMessage;
import asia.stampy.client.message.nack.NackMessage;
import asia.stampy.client.message.send.SendMessage;
import asia.stampy.client.message.stomp.StompMessage;
import asia.stampy.client.message.subscribe.SubscribeMessage;
import asia.stampy.client.message.unsubscribe.UnsubscribeMessage;
import asia.stampy.common.StampyLibrary;
import asia.stampy.common.message.AbstractBodyMessage;
import asia.stampy.common.message.AbstractBodyMessageHeader;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;
import asia.stampy.common.serialization.SerializationUtils;
import asia.stampy.server.message.connected.ConnectedMessage;
import asia.stampy.server.message.error.ErrorMessage;
import asia.stampy.server.message.message.MessageMessage;
import asia.stampy.server.message.receipt.ReceiptMessage;

/**
 * This class parses STOMP messages into {@link StampyMessage}s.
 */
@StampyLibrary(libraryName="stampy-core")
public class StompMessageParser {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** The Constant EOM. */
  public static final String EOM = "\000";

  /**
   * Parses the message.
   * 
   * @param <MSG>
   *          the generic type
   * @param stompMessage
   *          the stomp message
   * @return the msg
   * @throws UnparseableException
   *           the unparseable exception
   */
  public <MSG extends StampyMessage<?>> MSG parseMessage(String stompMessage) throws UnparseableException {
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new StringReader(stompMessage));

      String messageType = reader.readLine();

      StompMessageType type = StompMessageType.valueOf(messageType);

      List<String> headers = new ArrayList<String>();
      String hdr = reader.readLine();

      while (StringUtils.isNotEmpty(hdr)) {
        headers.add(hdr);
        hdr = reader.readLine();
      }

      String body = reader.readLine();
      body = body == null || body.equals(EOM) ? null : fillBody(body, reader);

      MSG msg = createStampyMessage(type, headers);

      if (!StringUtils.isEmpty(body) && msg instanceof AbstractBodyMessage<?>) {
        AbstractBodyMessage<?> abm = (AbstractBodyMessage<?>) msg;
        abm.setBody(isText(headers) ? body : convertToObject(body, abm.getHeader().getContentType()));
      }
      return msg;
    } catch (Exception e) {
      throw new UnparseableException("The message supplied cannot be parsed as a STOMP message", stompMessage, e);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          log.warn("Could not close reader", e);
        }
      }
    }
  }

  /**
   * Converts the specified string to an object based upon the specified content
   * type. Only base64 encoding is supported for Java objects.
   * 
   * @param body
   *          the body
   * @param contentType
   *          the content type
   * @return the object
   * @throws IllegalObjectException
   *           the illegal object exception
   * @throws ClassNotFoundException
   *           the class not found exception
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  protected Object convertToObject(String body, String contentType) throws IllegalObjectException,
      ClassNotFoundException, IOException {
    if (!AbstractBodyMessage.JAVA_BASE64_MIME_TYPE.equals(contentType)) {
      throw new NotImplementedException(
          "Subclass this class and override convertToObject to enable conversion using mime type " + contentType);
    }

    Object o = SerializationUtils.deserializeBase64(body);

    illegalObjectCheck(o);

    return o;
  }

  /**
   * Blank implementation; override to add any object checking logic.
   * 
   * @param o
   *          the o
   * @throws IllegalObjectException
   *           the illegal object exception
   */
  protected void illegalObjectCheck(Object o) throws IllegalObjectException {

  }

  /**
   * Checks if is text.
   * 
   * @param headers
   *          the headers
   * @return true, if is text
   */
  protected boolean isText(List<String> headers) {
    boolean text = false;
    boolean content = false;
    for (String hdr : headers) {
      if (hdr.contains(AbstractBodyMessageHeader.CONTENT_TYPE)) {
        content = true;
        text = hdr.contains("text/");
      }
    }

    return !content || (content && text);
  }

  /**
   * Creates the stampy message.
   * 
   * @param <MSG>
   *          the generic type
   * @param type
   *          the type
   * @param headers
   *          the headers
   * @return the msg
   * @throws UnparseableException
   *           the unparseable exception
   */
  @SuppressWarnings("unchecked")
  protected <MSG extends StampyMessage<?>> MSG createStampyMessage(StompMessageType type, List<String> headers)
      throws UnparseableException {

    MSG message = null;

    switch (type) {

    case ABORT:
      message = (MSG) new AbortMessage();
      break;
    case ACK:
      message = (MSG) new AckMessage();
      break;
    case BEGIN:
      message = (MSG) new BeginMessage();
      break;
    case COMMIT:
      message = (MSG) new CommitMessage();
      break;
    case CONNECT:
      message = (MSG) new ConnectMessage();
      break;
    case CONNECTED:
      message = (MSG) new ConnectedMessage();
      break;
    case DISCONNECT:
      message = (MSG) new DisconnectMessage();
      break;
    case ERROR:
      ErrorMessage error = new ErrorMessage();
      message = (MSG) error;
      break;
    case MESSAGE:
      MessageMessage mm = new MessageMessage();
      message = (MSG) mm;
      break;
    case NACK:
      message = (MSG) new NackMessage();
      break;
    case RECEIPT:
      message = (MSG) new ReceiptMessage();
      break;
    case SEND:
      SendMessage send = new SendMessage();
      message = (MSG) send;
      break;
    case STOMP:
      message = (MSG) new StompMessage();
      break;
    case SUBSCRIBE:
      message = (MSG) new SubscribeMessage();
      break;
    case UNSUBSCRIBE:
      message = (MSG) new UnsubscribeMessage();
      break;
    default:
      break;

    }

    message.getHeader();

    addHeaders(message, headers);

    return message;
  }

  private <MSG extends StampyMessage<?>> void addHeaders(MSG message, List<String> headers) throws UnparseableException {
    for (String header : headers) {
      StringTokenizer st = new StringTokenizer(header, ":");

      if (st.countTokens() < 2) {
        log.error("Cannot parse STOMP header {}", header);
        throw new UnparseableException("Cannot parse STOMP header " + header);
      }

      String key = st.nextToken();
      String value = header.substring(key.length() + 1);

      message.getHeader().addHeader(key, value);
    }
  }

  /**
   * Fills the body of the STOMP message.
   * 
   * @param body
   *          the body
   * @param reader
   *          the reader
   * @return the string
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  protected String fillBody(String body, BufferedReader reader) throws IOException {
    StringBuilder builder = new StringBuilder(trimEOM(body));

    String s = reader.readLine();

    while (s != null) {
      builder.append(trimEOM(s));
      s = reader.readLine();
    }

    return builder.toString();
  }

  /**
   * Trims the terminating byte.
   * 
   * @param s
   *          the s
   * @return the string
   */
  protected String trimEOM(String s) {
    String trimmed = s;
    if (s.contains(EOM)) {
      int idx = s.indexOf(EOM);
      trimmed = s.substring(0, idx);
    }

    return trimmed;
  }
}
