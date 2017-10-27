package org.jivesoftware.openfire.spi;

/**
 * ConnectionAcceptors are responsible for accepting new (typically socket) connections from peers.
 *
 * The configuration (but not the state) of an instance is intended to be immutable. When configuration changes are
 * needed, an instance needs to be replaced by a new instance.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public abstract class ConnectionAcceptor
{
    protected ConnectionConfiguration configuration;

    /**
     * Constructs a new instance which will accept new connections based on the provided configuration.
     *
     * The provided configuration is expected to be immutable. ConnectionAcceptor instances are not expected to handle
     * changes in configuration. When such changes are to be applied, an instance is expected to be replaced.
     *
     * Newly instantiated ConnectionAcceptors will not accept any connections before {@link #start()} is invoked.
     *
     * @param configuration The configuration for connections to be accepted (cannot be null).
     */
    public ConnectionAcceptor( ConnectionConfiguration configuration )
    {
        if (configuration == null)
        {
            throw new IllegalArgumentException( "Argument 'configuration' cannot be null" );
        }
        this.configuration = configuration;
    }

    /**
     * Makes the instance start accepting connections.
     *
     * An invocation of this method on an instance that is already started should have no effect (to the extend that the
     * instance should continue to accept connections without interruption or configuration changes).
     */
    abstract public void start();

    /**
     * Halts connection acceptation and gracefully releases resources.
     *
     * An invocation of this method on an instance that was not accepting connections should have no effect.
     *
     * Instances of this class do not support configuration changes (see class documentation). As a result, there is no
     * requirement that an instance that is stopped after it was running can successfully be restarted.
     */
    abstract public void stop();

    /**
     * Determines if this instance is currently in a state where it is actively serving connections.
     *
     * @return false when this instance is started and is currently being used to serve connections (otherwise true)
     */
    abstract public boolean isIdle();

    /**
     * Reloads the acceptor configuration, without causing a disconnect of already established connections.
     *
     * A best-effort reload will be attempted. Configuration changes that require a restart of connections,
     * such as the bind-address and port, will not be applied.
     *
     * The configuration that's provided will replace the existing configuration. A restart of the connection
     * acceptor after this method was invoked will apply all configuration changes.
     *
     * @param configuration The configuration for connections to be accepted (cannot be null).
     */
    abstract public void reconfigure( ConnectionConfiguration configuration );
}
