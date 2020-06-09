package org.jivesoftware.openfire.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class SessionEventDispatcher
{
    private static final Logger Log = LoggerFactory.getLogger( SessionEventDispatcher.class );

    private static Set<SessionListener> listeners = new CopyOnWriteArraySet<>();

    private SessionEventDispatcher()
    {
        // Not instantiable.
    }

    /**
     * Adds a {@link org.jivesoftware.openfire.http.SessionListener} to this session. The listener
     * will be notified of changes to the session.
     *
     * @param listener the listener which is being added to the session.
     */
    public static void addListener( SessionListener listener )
    {
        if ( listener == null )
        {
            throw new NullPointerException();
        }
        listeners.add( listener );
    }

    /**
     * Removes a {@link org.jivesoftware.openfire.http.SessionListener} from this session. The
     * listener will no longer be updated when an event occurs on the session.
     *
     * @param listener the session listener that is to be removed.
     */
    public static void removeListener( SessionListener listener )
    {
        listeners.remove( listener );
    }

    /**
     * Dispatches an event related to a particular BOSH session to all registered listeners.
     *
     * @param session The session that relates to the event (can be null when the event type is 'pre_session_created').
     * @param eventType The type of the event (cannot be null).
     * @param connection The connection where the event originated.
     * @param context The servlet context of the event
     */
    public static void dispatchEvent( HttpSession session, EventType eventType, HttpConnection connection, AsyncContext context )
    {
        for ( final SessionListener listener : listeners )
        {
            try
            {
                switch ( eventType )
                {
                    case connection_opened:
                        listener.connectionOpened( context, session, connection );
                        listener.connectionOpened( session, connection );
                        break;

                    case connection_closed:
                        listener.connectionClosed( context, session, connection );
                        listener.connectionClosed( session, connection );
                        break;

                    case pre_session_created:
                        listener.preSessionCreated( context );
                        break;

                    case post_session_created:
                        listener.postSessionCreated( context, session );
                        break;

                    case session_closed:
                        listener.sessionClosed( session );
                        break;

                    default:
                        throw new IllegalStateException( "An unexpected and unsupported event type was used: " + eventType );
                }
            }
            catch ( Exception e )
            {
                Log.warn( "An exception occurred while dispatching an event of type {}", eventType, e );
            }
        }
    }

    public enum EventType
    {
        connection_opened,
        connection_closed,
        pre_session_created,
        post_session_created,
        session_closed
    }
}
