package org.igniterealtime.openfire.plugins.externalservicediscovery;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

/**
 * Simple tests that verify the implementation of {@link Service}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ServiceTest
{
    /**
     * Happy flow - should not cause any issues.
     */
    @Test
    public void testPackageConstructor()
    {
        int databaseId = -1;
        String name = "description";
        String host = "host";
        Integer port = 123;
        Boolean restricted = false;
        String transport = "udp";
        String type = "turn";
        String username = "username";
        String password = "password";
        String sharedSecret = "secret";
        new Service( databaseId, name, host, port, restricted, transport, type, username, password, sharedSecret );
    }

    /**
     * Verifies that an IllegalArgumentException is thrown by the constructor when the provided 'host' argument value is null.
     */
    @Test( expected = IllegalArgumentException.class )
    public void testNullHost()
    {
        int databaseId = -1;
        String name = "description";
        String host = null;
        Integer port = 123;
        Boolean restricted = false;
        String transport = "udp";
        String type = "turn";
        String username = "username";
        String password = "password";
        String sharedSecret = "secret";
        new Service( databaseId, name, host, port, restricted, transport, type, username, password, sharedSecret );
    }

    /**
     * Verifies that an IllegalArgumentException is thrown by the constructor when the provided 'type' argument value is null.
     */
    @Test( expected = IllegalArgumentException.class )
    public void testNullType()
    {
        int databaseId = -1;
        String name = "description";
        String host = "host";
        Integer port = 123;
        Boolean restricted = false;
        String transport = "udp";
        String type = null;
        String username = "username";
        String password = "password";
        String sharedSecret = "secret";
        new Service( databaseId, name, host, port, restricted, transport, type, username, password, sharedSecret );
    }

    /**
     * Verifies that the implementation adheres to the contract specified in {@link Object#equals(Object)}.
     */
    @Test
    public void equalsContract()
    {
        EqualsVerifier.forClass( Service.class )
            .withNonnullFields( "host", "type" ) // checked by constructor.
            .verify();
    }
}
