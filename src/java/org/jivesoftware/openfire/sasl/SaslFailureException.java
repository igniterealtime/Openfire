package org.jivesoftware.openfire.sasl;

import javax.security.sasl.SaslException;

/**
 * A SaslException with XMPP 'failure' context.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class SaslFailureException extends SaslException
{
    private final Failure failure;

    public SaslFailureException( Failure failure, String message )
    {
        super( message );
        this.failure = failure;
    }

    public SaslFailureException( Failure failure )
    {
        this.failure = failure;
    }

    public SaslFailureException( String detail, Failure failure )
    {
        super( detail );
        this.failure = failure;
    }

    public SaslFailureException( String detail, Throwable ex, Failure failure )
    {
        super( detail, ex );
        this.failure = failure;
    }

    public Failure getFailure()
    {
        return failure;
    }
}
