package org.jivesoftware.openfire.keystore;

import java.io.File;
import java.util.Arrays;

/**
 * Certificate stores are configured using a defined set of properties. This is a wrapper class for all of them.
 *
 * Instances of this class are immutable and safe for use by multiple concurrent threads.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CertificateStoreConfiguration
{
    protected final String type;
    protected final File file;
    protected final char[] password;

    /**
     * Creates a new instance.
     *
     * @param type The store type (jks, jceks, pkcs12, etc). Cannot be null or an empty string.
     * @param file  The file-system based representation of the store (cannot be null).
     * @param password the password used to check the integrity of the store, the password used to unlock the store, or null.
     */
    public CertificateStoreConfiguration( String type, File file, char[] password )
    {
        if ( type == null || type.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'type' cannot be null or an empty string." );
        }
        if ( file == null )
        {
            throw new IllegalArgumentException( "Argument 'file' cannot be null." );
        }
        this.type = type;
        this.file = file;
        this.password = password;
    }

    public String getType()
    {
        return type;
    }

    public File getFile()
    {
        return file;
    }

    public char[] getPassword()
    {
        return password;
    }

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( !( o instanceof CertificateStoreConfiguration ) )
        {
            return false;
        }

        CertificateStoreConfiguration that = (CertificateStoreConfiguration) o;

        if ( !type.equals( that.type ) )
        {
            return false;
        }
        if ( !file.equals( that.file ) )
        {
            return false;
        }
        return Arrays.equals( password, that.password );

    }

    @Override
    public int hashCode()
    {
        int result = type.hashCode();
        result = 31 * result + file.hashCode();
        result = 31 * result + ( password != null ? Arrays.hashCode( password ) : 0 );
        return result;
    }

    @Override
    public String toString()
    {
        return "CertificateStoreConfiguration{" +
                "type='" + type + '\'' +
                ", file=" + file +
                ", password hashcode=" + password.hashCode() + // java.lang.Array.hashCode inherits from Object. As it is a reference, it should be safe to log and useful enough to compare against other passwords.
                '}';
    }
}
