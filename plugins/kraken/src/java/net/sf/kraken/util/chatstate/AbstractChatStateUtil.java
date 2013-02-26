/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.util.chatstate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.Immutable;
import net.jcip.annotations.ThreadSafe;
import net.sf.kraken.type.ChatStateType;

import org.xmpp.packet.JID;

/**
 * Instances of this class maintain state for active "chat state notifications"
 * in conversations between two XMPP entities. Additionally, as most chat states
 * are intended to be followed up with a new chat state after a specific period
 * of idleness, instances of this class schedule chat state notification changes
 * after such a period elapses.
 * 
 * This class is thread safe.
 * 
 * @author Guus der Kinderen
 * @see <a
 *      href="http://xmpp.org/extensions/xep-0085.html">XEP-0085:&nbsp;Chat&nbsp;State&nbsp;Notifications</a>
 */
@ThreadSafe
public abstract class AbstractChatStateUtil {

    /**
     * Mutual exclusion object used to ensure proper thread-safetyness.
     */
    // TODO replace the utilization of this mutex with more specific synchronization, for instance based on canonical representations of ChatStateSessions. Make sure that the canonical representations themselves do eventually get garbage collected though (or OOMs will occur)!
    private final Object mutex = new Object();

    /**
     * Maintains the current chat state for a particular conversation.
     */
    @GuardedBy("mutex")
    private final Map<ChatStateSession, ChatStateType> currentStates = new HashMap<ChatStateSession, ChatStateType>();

    /**
     * Maintains scheduled chat state changes for a particular conversation.
     * Each conversation can have at most one scheduled chat state change.
     */
    @GuardedBy("mutex")
    private final Map<ChatStateSession, ScheduledFuture<?>> pendingStateChanges = new HashMap<ChatStateSession, ScheduledFuture<?>>();

    /**
     * A threadpool that is used to perform the scheduld state changes.
     */
    private final static ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2);

    /**
     * Changes the chat state of the sender to 'composing' in the context of a
     * conversation with the receiver. Additionally, a future chat state change
     * to 'paused' is scheduled, replacing any existing scheduled chat state
     * changes.
     * 
     * If execution of this method changes the current chat state,
     * {@link #sendIsComposing(JID, JID)} will be triggered exactly once.
     * 
     * The delay after which the change to the future chat state will be reset
     * no matter what the current chat state is.
     * 
     * @param sender
     *            The entity of which the chat state is changing.
     * @param receiver
     *            The entity that is to receive a chat state change
     *            notification.
     */
    public void isComposing(JID sender, JID receiver) {
        final ChatStateSession session = new ChatStateSession(sender, receiver);
        final ChatStateType newState = ChatStateType.composing;

        synchronized (mutex) {
            final ChatStateType previousState = currentStates.put(session, newState);
            if (previousState != newState) {
                sendIsComposing(sender, receiver);
            }
            // Replace any existing timeout with a new timeout: should go to
            // 'paused' in 30 seconds
            scheduleStateChange(30, TimeUnit.SECONDS, ChatStateType.paused, session);
        }
    }

    /**
     * Sends out a notification to the XMPP entity identified by 'receiver' that
     * the state change of the XMPP entity identified by 'sender' changed to
     * 'composing'.
     * 
     * @param sender
     *            The entity of which the chat state is changing.
     * @param receiver
     *            The entity that is to receive a chat state change
     *            notification.
     */
    public abstract void sendIsComposing(JID sender, JID receiver);

    /**
     * Changes the chat state of the sender to 'active' in the context of a
     * conversation with the receiver. Additionally, a future chat state change
     * to 'inactive' is scheduled, replacing any existing scheduled chat state
     * changes.
     * 
     * If execution of this method changes the current chat state,
     * {@link #sendIsActive(JID, JID)} will be triggered exactly once.
     * 
     * The delay after which the change to the future chat state will be reset
     * no matter what the current chat state is.
     * 
     * @param sender
     *            The entity of which the chat state is changing.
     * @param receiver
     *            The entity that is to receive a chat state change
     *            notification.
     */
    public void isActive(JID sender, JID receiver) {
        final ChatStateSession session = new ChatStateSession(sender, receiver);
        final ChatStateType newState = ChatStateType.active;

        synchronized (mutex) {
            final ChatStateType previousState = currentStates.put(session, newState);
            if (previousState != newState) {
                sendIsActive(sender, receiver);
            }
            // Replace any existing timeout with a new timeout: should go to
            // 'inactive' in 2 minutes
            scheduleStateChange(120, TimeUnit.SECONDS, ChatStateType.inactive, session);
        }
    }

    /**
     * Sends out a notification to the XMPP entity identified by 'receiver' that
     * the state change of the XMPP entity identified by 'sender' changed to
     * 'active'.
     * 
     * @param sender
     *            The entity of which the chat state is changing.
     * @param receiver
     *            The entity that is to receive a chat state change
     *            notification.
     */
    public abstract void sendIsActive(JID sender, JID receiver);

    /**
     * Changes the chat state of the sender to 'paused' in the context of a
     * conversation with the receiver. Additionally, a future chat state change
     * to 'inactive' is scheduled, replacing any existing scheduled chat state
     * changes.
     * 
     * If execution of this method changes the current chat state,
     * {@link #sendIsPaused(JID, JID)} will be triggered exactly once.
     * 
     * The delay after which the change to the future chat state will be reset
     * no matter what the current chat state is.
     * 
     * @param sender
     *            The entity of which the chat state is changing.
     * @param receiver
     *            The entity that is to receive a chat state change
     *            notification.
     */
    public void isPaused(JID sender, JID receiver) {
        final ChatStateSession session = new ChatStateSession(sender, receiver);
        final ChatStateType newState = ChatStateType.paused;

        synchronized (mutex) {
            final ChatStateType previousState = currentStates.put(session, newState);
            if (previousState != newState) {
                sendIsPaused(sender, receiver);
            }
            // Replace any existing timeout with a new timeout: should go to
            // 'inactive' in 90 seconds (2 minutes - 30 seconds!)
            scheduleStateChange(90, TimeUnit.SECONDS, ChatStateType.inactive, session);
        }
    }

    /**
     * Sends out a notification to the XMPP entity identified by 'receiver' that
     * the state change of the XMPP entity identified by 'sender' changed to
     * 'paused'.
     * 
     * @param sender
     *            The entity of which the chat state is changing.
     * @param receiver
     *            The entity that is to receive a chat state change
     *            notification.
     */
    public abstract void sendIsPaused(JID sender, JID receiver);

    /**
     * Changes the chat state of the sender to 'inactive' in the context of a
     * conversation with the receiver. Additionally, a future chat state change
     * to 'gone' is scheduled, replacing any existing scheduled chat state
     * changes.
     * 
     * If execution of this method changes the current chat state,
     * {@link #sendIsInactive(JID, JID)} will be triggered exactly once.
     * 
     * The delay after which the change to the future chat state will be reset
     * no matter what the current chat state is.
     * 
     * @param sender
     *            The entity of which the chat state is changing.
     * @param receiver
     *            The entity that is to receive a chat state change
     *            notification.
     */
    public void isInactive(JID sender, JID receiver) {
        final ChatStateSession session = new ChatStateSession(sender, receiver);
        final ChatStateType newState = ChatStateType.inactive;

        synchronized (mutex) {
            final ChatStateType previousState = currentStates.put(session, newState);
            if (previousState != newState) {
                sendIsInactive(sender, receiver);
            }
            // Replace any existing timeout with a new timeout: should go to
            // 'gone'
            // in 8 minutes (10 minutes - 2 minutes!)
            scheduleStateChange(480, TimeUnit.SECONDS, ChatStateType.gone, session);
        }
    }

    /**
     * Sends out a notification to the XMPP entity identified by 'receiver' that
     * the state change of the XMPP entity identified by 'sender' changed to
     * 'inactive'.
     * 
     * @param sender
     *            The entity of which the chat state is changing.
     * @param receiver
     *            The entity that is to receive a chat state change
     *            notification.
     */
    public abstract void sendIsInactive(JID sender, JID receiver);

    /**
     * Changes the chat state of the sender to 'gone' in the context of a
     * conversation with the receiver.
     * 
     * If execution of this method changes the current chat state,
     * {@link #sendIsGone(JID, JID)} will be triggered exactly once.
     * 
     * Any scheduled future chat state will be canceled no matter what the
     * current chat state is.
     * 
     * @param sender
     *            The entity of which the chat state is changing.
     * @param receiver
     *            The entity that is to receive a chat state change
     *            notification.
     */
    public void isGone(JID sender, JID receiver) {
        // a somewhat special case. Chat state 'gone' is represented by having
        // no chat state in the map.
        final ChatStateSession session = new ChatStateSession(sender, receiver);
        // final ChatStateType newState = ChatStateType.gone;

        synchronized (mutex) {
            // Remove this conversation from the HashMap (this prevents memory
            // leaks).
            final ChatStateType previousState = currentStates.remove(session);
            if (previousState != null) {
                sendIsGone(sender, receiver);
            }

            // remove any existing timeouts
            final ScheduledFuture<?> oldFuture = pendingStateChanges.remove(session);
            if (oldFuture != null) {
                oldFuture.cancel(false);
            }
        }
    }

    /**
     * Sends out a notification to the XMPP entity identified by 'receiver' that
     * the state change of the XMPP entity identified by 'sender' changed to
     * 'gone'.
     * 
     * @param sender
     *            The entity of which the chat state is changing.
     * @param receiver
     *            The entity that is to receive a chat state change
     *            notification.
     */
    public abstract void sendIsGone(JID sender, JID receiver);

    /**
     * Schedules a change of chat state for a particular session. Any pending
     * chat state changes are canceled and replaced by one new chat state
     * change.
     * 
     * @param delay
     *            The delay after which the chat state change should be
     *            executed.
     * @param unit
     *            The unit of time in which delay is measured
     * @param state
     *            The chat state to what should be changed.
     * @param session
     *            The session for which the chat state will change.
     */
    public void scheduleStateChange(long delay, TimeUnit unit, ChatStateType state, ChatStateSession session) {
        final Runnable task = new GoToNextState(session, state);

        synchronized (mutex) {
            final ScheduledFuture<?> newFuture = EXECUTOR.schedule(task, delay, unit);
            final ScheduledFuture<?> oldFuture = pendingStateChanges.put(session, newFuture);
            if (oldFuture != null) {
                oldFuture.cancel(false);
            }
        }
    }

    /**
     * A Runnable that will change the current state of a particular
     * ChatStateSession.
     * 
     * Instances of this class are immutable.
     * 
     * @author Guus der Kinderen
     */
    @Immutable
    private class GoToNextState implements Runnable {

        public final ChatStateSession session;
        public final ChatStateType nextState;

        public GoToNextState(ChatStateSession session, ChatStateType nextState) {
            this.session = session;
            this.nextState = nextState;
        }

        public void run() {
            final JID sender = session.sender;
            final JID receiver = session.receiver;

            // remove this change from the 'pending' collection.
            synchronized (mutex) {
                pendingStateChanges.remove(session);

                switch (nextState) {
                    case active:
                        isActive(sender, receiver);
                        break;

                    case composing:
                        isComposing(sender, receiver);
                        break;

                    case gone:
                        isGone(sender, receiver);
                        break;

                    case inactive:
                        isInactive(sender, receiver);
                        break;

                    case paused:
                        isPaused(sender, receiver);
                        break;

                    default:
                        // The code should include a case for every state.
                        throw new AssertionError(nextState);
                }
            }
        }
    }

    /**
     * Instances of this class represent a chat conversation.
     * 
     * Instances of this class are immutable.
     * 
     * @author Guus der Kinderen
     */
    // TODO include optional thread-id
    @Immutable
    private static class ChatStateSession {

        public final JID sender;
        public final JID receiver;

        public ChatStateSession(JID sender, JID receiver) {
            this.sender = sender;
            this.receiver = receiver;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((receiver == null) ? 0 : receiver.hashCode());
            result = prime * result + ((sender == null) ? 0 : sender.hashCode());
            return result;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ChatStateSession other = (ChatStateSession) obj;
            if (receiver == null) {
                if (other.receiver != null) {
                    return false;
                }
            }
            else if (!receiver.equals(other.receiver)) {
                return false;
            }
            if (sender == null) {
                if (other.sender != null) {
                    return false;
                }
            }
            else if (!sender.equals(other.sender)) {
                return false;
            }
            return true;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("ChatStateSession [receiver=");
            builder.append(receiver);
            builder.append(", sender=");
            builder.append(sender);
            builder.append("]");
            return builder.toString();
        }
    }
}
