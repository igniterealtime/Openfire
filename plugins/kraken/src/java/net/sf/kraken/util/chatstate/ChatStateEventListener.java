package net.sf.kraken.util.chatstate;

import java.util.EventListener;

/**
 * The listener interface for receiving {@link ChatStateChangeEvent}s.
 * 
 * @author Guus der Kinderen
 */
public interface ChatStateEventListener extends EventListener {

    /**
     * Invoked when a ChatStateChangeEvent occurs.
     * 
     * @param event
     *            the event that occurs.
     */
    void chatStateChange(ChatStateChangeEvent event);
}
