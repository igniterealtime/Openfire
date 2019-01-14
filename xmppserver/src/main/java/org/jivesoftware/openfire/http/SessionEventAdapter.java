package org.jivesoftware.openfire.http;

import javax.servlet.AsyncContext;

/**
 * An abstract adapter class for receiving httpbind/bosh session events.
 * The methods in this class are empty. This class exists as convenience for creating listener objects.
 */
public class SessionEventAdapter implements SessionListener
{
    @Override
    public void connectionOpened( final AsyncContext context, final HttpSession session, final HttpConnection connection )
    {
    }

    @Override
    public void connectionOpened( final HttpSession session, final HttpConnection connection )
    {
    }

    @Override
    public void connectionClosed( final AsyncContext context, final HttpSession session, final HttpConnection connection )
    {
    }

    @Override
    public void connectionClosed( final HttpSession session, final HttpConnection connection )
    {
    }

    @Override
    public void preSessionCreated( final AsyncContext context )
    {
    }

    @Override
    public void postSessionCreated( final AsyncContext context, final HttpSession session )
    {
    }

    @Override
    public void sessionClosed( final HttpSession session )
    {
    }
}
