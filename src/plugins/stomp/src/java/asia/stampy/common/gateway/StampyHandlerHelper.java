package asia.stampy.common.gateway;

import java.lang.invoke.MethodHandles;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.client.message.ClientMessageHeader;
import asia.stampy.common.StampyLibrary;
import asia.stampy.common.heartbeat.StampyHeartbeatContainer;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.parsing.StompMessageParser;
import asia.stampy.common.parsing.UnparseableException;
import asia.stampy.server.message.error.ErrorMessage;

/**
 * This class implements methods shared across handler implementations.
 * @author burton
 *
 */
@Resource
@StampyLibrary(libraryName="stampy-client-server")
public class StampyHandlerHelper {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private StompMessageParser parser = new StompMessageParser();

  private StampyHeartbeatContainer heartbeatContainer;

  private AbstractStampyMessageGateway gateway;

  private UnparseableMessageHandler unparseableMessageHandler = new DefaultUnparseableMessageHandler();

  /**
   * Handle unexpected error.
   * 
   * @param hostPort
   *          the host port
   * @param msg
   *          the msg
   * @param sm
   *          the sm
   * @param e
   *          the e
   */
  public void handleUnexpectedError(HostPort hostPort, String msg, StampyMessage<?> sm, Exception e) {
    try {
      if (sm == null) {
        errorHandle(e, hostPort);
      } else {
        errorHandle(sm, e, hostPort);
      }
    } catch (Exception e1) {
      log.error("Unexpected exception sending error message for {}", hostPort, e1);
    }
  }

  /**
   * Handle unparseable message.
   * 
   * @param hostPort
   *          the host port
   * @param msg
   *          the msg
   * @param e
   *          the e
   */
  public void handleUnparseableMessage(HostPort hostPort, String msg, UnparseableException e) {
    log.debug("Unparseable message, delegating to unparseable message handler");
    try {
      getUnparseableMessageHandler().unparseableMessage(msg, hostPort);
    } catch (Exception e1) {
      try {
        errorHandle(e1, hostPort);
      } catch (Exception e2) {
        log.error("Could not parse message {} for {}", msg, hostPort, e);
        log.error("Unexpected exception sending error message for {}", hostPort, e2);
      }
    }
  }

  /**
   * Error handle. Logs the error.
   * 
   * @param message
   *          the message
   * @param e
   *          the e
   * @param hostPort
   *          the host port
   * @throws Exception
   *           the exception
   */
  protected void errorHandle(StampyMessage<?> message, Exception e, HostPort hostPort) throws Exception {
    log.error("Handling error, sending error message to {}", hostPort, e);
    String receipt = message.getHeader().getHeaderValue(ClientMessageHeader.RECEIPT);
    ErrorMessage error = new ErrorMessage(StringUtils.isEmpty(receipt) ? "n/a" : receipt);
    error.getHeader().setMessageHeader("Could not execute " + message.getMessageType() + " - " + e.getMessage());
    getGateway().sendMessage(error.toStompMessage(true), hostPort);
  }

  /**
   * Error handle. Logs the error.
   * 
   * @param e
   *          the e
   * @param hostPort
   *          the host port
   * @throws Exception
   *           the exception
   */
  protected void errorHandle(Exception e, HostPort hostPort) throws Exception {
    log.error("Handling error, sending error message to {}", hostPort, e);
    ErrorMessage error = new ErrorMessage("n/a");
    error.getHeader().setMessageHeader(e.getMessage());
    getGateway().sendMessage(error.toStompMessage(true), hostPort);
  }

  /**
   * Checks if the message is a heartbeat.
   * 
   * @param msg
   *          the msg
   * @return true, if is heartbeat
   */
  public boolean isHeartbeat(String msg) {
    return msg.equals(StampyHeartbeatContainer.HB1) || msg.equals(StampyHeartbeatContainer.HB2);
  }

  /**
   * Checks if is valid object. Must be a string.
   * 
   * @param message
   *          the message
   * @return true, if is valid object
   */
  public boolean isValidObject(Object message) {
    return message instanceof String;
  }

  /**
   * Reset heartbeat.
   * 
   * @param hostPort
   *          the host port
   */
  public void resetHeartbeat(HostPort hostPort) {
    getHeartbeatContainer().reset(hostPort);
  }

  public StompMessageParser getParser() {
    return parser;
  }

  public void setParser(StompMessageParser parser) {
    this.parser = parser;
  }

  public StampyHeartbeatContainer getHeartbeatContainer() {
    return heartbeatContainer;
  }

  public void setHeartbeatContainer(StampyHeartbeatContainer heartbeatContainer) {
    this.heartbeatContainer = heartbeatContainer;
  }

  public AbstractStampyMessageGateway getGateway() {
    return gateway;
  }

  public void setGateway(AbstractStampyMessageGateway gateway) {
    this.gateway = gateway;
  }

  public UnparseableMessageHandler getUnparseableMessageHandler() {
    return unparseableMessageHandler;
  }

  public void setUnparseableMessageHandler(UnparseableMessageHandler unparseableMessageHandler) {
    this.unparseableMessageHandler = unparseableMessageHandler;
  }

}
