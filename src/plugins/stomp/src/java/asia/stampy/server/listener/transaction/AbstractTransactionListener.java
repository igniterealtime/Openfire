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
package asia.stampy.server.listener.transaction;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.client.message.abort.AbortMessage;
import asia.stampy.client.message.begin.BeginMessage;
import asia.stampy.client.message.commit.CommitMessage;
import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;

/**
 * This class manages transactional boundaries, ensuring that a transaction has
 * been started prior to an {@link StompMessageType#ABORT} or.
 * 
 * {@link StompMessageType#COMMIT} and that a transaction is began only once.
 */
@StampyLibrary(libraryName = "stampy-client-server")
public abstract class AbstractTransactionListener<SVR extends AbstractStampyMessageGateway> implements StampyMessageListener {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** The active transactions. */
  protected Map<HostPort, Queue<String>> activeTransactions = new ConcurrentHashMap<HostPort, Queue<String>>();
  private SVR gateway;

  private static StompMessageType[] TYPES = { StompMessageType.ABORT, StompMessageType.BEGIN, StompMessageType.COMMIT,
      StompMessageType.DISCONNECT };

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.gateway.StampyMessageListener#getMessageTypes()
   */
  @Override
  public StompMessageType[] getMessageTypes() {
    return TYPES;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * asia.stampy.common.gateway.StampyMessageListener#isForMessage(asia.stampy
   * .common.message.StampyMessage)
   */
  @Override
  public boolean isForMessage(StampyMessage<?> message) {
    return true;
  }

  /*
   * (non-Javadoc)
   * 
   * @see asia.stampy.common.gateway.StampyMessageListener#messageReceived(asia.
   * stampy.common.message.StampyMessage, asia.stampy.common.HostPort)
   */
  @Override
  public void messageReceived(StampyMessage<?> message, HostPort hostPort) throws Exception {
    switch (message.getMessageType()) {
    case ABORT:
      abort(hostPort, ((AbortMessage) message).getHeader().getTransaction());
      break;
    case BEGIN:
      begin(hostPort, ((BeginMessage) message).getHeader().getTransaction());
      break;
    case COMMIT:
      commit(hostPort, ((CommitMessage) message).getHeader().getTransaction());
      break;
    case DISCONNECT:
      logOutstandingTransactions(hostPort);
      break;
    default:
      break;

    }

  }

  private void logOutstandingTransactions(HostPort hostPort) {
    Queue<String> q = getTransactions(hostPort);
    if (q.isEmpty()) return;

    for (String transaction : q) {
      log.warn("Disconnect received, discarding outstanding transaction {}", transaction);
    }
  }

  private void commit(HostPort hostPort, String transaction) throws TransactionNotStartedException {
    removeActiveTransaction(hostPort, transaction, "committed");
  }

  private void abort(HostPort hostPort, String transaction) throws TransactionNotStartedException {
    removeActiveTransaction(hostPort, transaction, "aborted");
  }

  private void begin(HostPort hostPort, String transaction) throws TransactionAlreadyStartedException {
    if (isNoTransaction(hostPort, transaction)) {
      log.info("Starting transaction {} for {}", transaction, hostPort);
      Queue<String> q = getTransactions(hostPort);
      q.add(transaction);
    }
  }

  private boolean isNoTransaction(HostPort hostPort, String transaction) throws TransactionAlreadyStartedException {
    Queue<String> q = getTransactions(hostPort);
    if (q.contains(transaction)) {
      String error = "Transaction already started";
      throw new TransactionAlreadyStartedException(error);
    }

    return true;
  }

  private void removeActiveTransaction(HostPort hostPort, String transaction, String function)
      throws TransactionNotStartedException {
    if (isTransactionStarted(hostPort, transaction)) {
      Object[] parms = { transaction, hostPort, function };
      log.info("Transaction id {} for {} {}", parms);
      Queue<String> q = getTransactions(hostPort);
      q.remove(transaction);
    }
  }

  private Queue<String> getTransactions(HostPort hostPort) {
    Queue<String> transactions = activeTransactions.get(hostPort);
    if (transactions == null) {
      transactions = new ConcurrentLinkedQueue<String>();
      activeTransactions.put(hostPort, transactions);
    }

    return transactions;
  }

  private boolean isTransactionStarted(HostPort hostPort, String transaction) throws TransactionNotStartedException {
    Queue<String> q = getTransactions(hostPort);
    if (!q.contains(transaction)) {
      String error = "Transaction not started";
      log.error(error);
      throw new TransactionNotStartedException(error);
    }

    return true;
  }

  /**
   * Gets the gateway.
   * 
   * @return the gateway
   */
  public SVR getGateway() {
    return gateway;
  }

  /**
   * Inject the {@link AbstractStampyMessageGateway} on system startup.
   * 
   * @param gateway
   *          the new gateway
   */
  public void setGateway(SVR gateway) {
    this.gateway = gateway;
  }

  /**
   * Configure the gateway to clean up the map of active transactions on session
   * termination.
   */
  protected abstract void ensureCleanup();

}
