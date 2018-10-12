package org.jivesoftware.openfire.keystore;

/**
 * A checked exception that indicates problems related to Certificate Store functionality.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CertificateStoreConfigException extends Exception
{
    public CertificateStoreConfigException()
    {
    }

    public CertificateStoreConfigException( String message )
    {
        super( message );
    }

    public CertificateStoreConfigException( String message, Throwable cause )
    {
        super( message, cause );
    }

    public CertificateStoreConfigException( Throwable cause )
    {
        super( cause );
    }

    public CertificateStoreConfigException( String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace )
    {
        super( message, cause, enableSuppression, writableStackTrace );
    }
}
