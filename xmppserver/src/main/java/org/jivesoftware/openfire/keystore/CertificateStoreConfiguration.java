package org.jivesoftware.openfire.keystore;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

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
    protected final File backupDirectory;

    /**
     * Creates a new instance.
     *
     * @param type The store type (jks, jceks, pkcs12, etc). Cannot be null or an empty string.
     * @param file  The file-system based representation of the store (cannot be null).
     * @param password the password used to check the integrity of the store, the password used to unlock the store, or null.
     * @param backupDirectory the directory in which the backup of the original keystore should be saved
     */
    public CertificateStoreConfiguration( String type, File file, char[] password, File backupDirectory )
    {
        if ( type == null || type.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'type' cannot be null or an empty string." );
        }
        if ( file == null )
        {
            throw new IllegalArgumentException( "Argument 'file' cannot be null." );
        }
        if ( backupDirectory == null )
        {
            throw new IllegalArgumentException( "Argument 'backupDirectory' cannot be null." );
        }
        this.type = type;
        this.file = file;
        this.password = password;
        this.backupDirectory = backupDirectory;
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

    public File getBackupDirectory()
    {
        return backupDirectory;
    }

    @Override
    public boolean equals( final Object o )
    {
        if ( this == o ) { return true; }
        if ( o == null || getClass() != o.getClass() ) { return false; }
        final CertificateStoreConfiguration that = (CertificateStoreConfiguration) o;
        return Objects.equals( type, that.type ) &&
            Objects.equals( file, that.file ) &&
            Arrays.equals( password, that.password ) &&
            Objects.equals( backupDirectory, that.backupDirectory );
    }

    @Override
    public int hashCode()
    {
        int result = Objects.hash( type, file, backupDirectory );
        result = 31 * result + Arrays.hashCode( password );
        return result;
    }

    @Override
    public String toString()
    {
        return "CertificateStoreConfiguration{" +
                "type='" + type + '\'' +
                ", file=" + file +
                ", password hashcode=" + password.hashCode() + // java.lang.Array.hashCode inherits from Object. As it is a reference, it should be safe to log and useful enough to compare against other passwords.
                ", backupDirectory=" + backupDirectory +
                '}';
    }
}
