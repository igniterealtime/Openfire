package org.jivesoftware.util;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.jsmpp.bean.*;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.TimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A service to send SMS messages.<p>
 *
 * This class is configured with a set of Jive properties. Note that each service provider can require a different set
 * of properties to be set.
 * <ul>
 * <li>{@code sms.smpp.connections.maxAmount} -- the maximum amount of connections. The default value is one.
 * <li>{@code sms.smpp.connections.idleMillis} -- time (in ms) after which idle connections are allowed to be evicted. Defaults to two minutes.
 * <li>{@code sms.smpp.host} -- the host name of your SMPP Server or SMSC, i.e. smsc.example.org. The default value is "localhost".
 * <li>{@code sms.smpp.port} -- the port on which the SMSC is listening. Defaults to 2775.
 * <li>{@code sms.smpp.systemId} -- the 'user name' to use when connecting to the SMSC.
 * <li>{@code sms.smpp.password} -- the password that authenticates the systemId value when connecting to the SMSC.
 * <li>{@code sms.smpp.systemType} -- an optional system type, which, if defined, will be used when connecting to the SMSC.
 * <li>{@code sms.smpp.receive.ton} -- The type-of-number value for 'receiving' SMS messages. Defaults to 'UNKNOWN'.
 * <li>{@code sms.smpp.receive.npi} -- The number-plan-indicator value for 'receiving' SMS messages. Defaults to 'UNKNOWN'.
 * <li>{@code sms.smpp.source.ton} -- The type-of-number value for the source of SMS messages. Defaults to 'UNKNOWN'.
 * <li>{@code sms.smpp.source.npi} -- The number-plan-indicator value for the source of SMS messages. Defaults to 'UNKNOWN'.
 * <li>{@code sms.smpp.source.address} -- The source address of SMS messages.
 * <li>{@code sms.smpp.destination.ton} -- The type-of-number value for the destination of SMS messages. Defaults to 'UNKNOWN'.
 * <li>{@code sms.smpp.destination.npi} -- The number-plan-indicator value for the destination of SMS messages. Defaults to 'UNKNOWN'.
 * </ul>
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class SmsService
{
    private static final Logger Log = LoggerFactory.getLogger( SmsService.class );

    private static TimeFormatter timeFormatter = new AbsoluteTimeFormatter();

    private static SmsService INSTANCE;

    public static synchronized SmsService getInstance()
    {
        if ( INSTANCE == null )
        {
            INSTANCE = new SmsService();
        }

        return INSTANCE;
    }

    /**
     * Pool of SMPP sessions that is used to transmit messages to the SMSC.
     */
    private final SMPPSessionPool sessionPool;

    private SmsService()
    {
        sessionPool = new SMPPSessionPool();
        PropertyEventDispatcher.addListener( sessionPool );
    }

    /**
     * Causes a new SMS message to be sent.
     *
     * Note that the message is sent asynchronously. This method does not block. A successful invocation does not
     * guarantee successful delivery
     *
     * @param message   The body of the message (cannot be null or empty).
     * @param recipient The address / phone number to which the message is to be send (cannot be null or empty).
     */
    public void send( String message, String recipient )
    {
        if ( message == null || message.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'message' cannot be null or an empty String." );
        }

        if ( recipient == null || recipient.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'recipient' cannot be null or an empty String." );
        }

        TaskEngine.getInstance().submit( new SmsTask( sessionPool, message, recipient ) );
    }

    /**
     * Causes a new SMS message to be sent.
     *
     * This method differs from {@link #send(String, String)} in that the message is sent before this method returns,
     * rather than queueing the messages to be sent later (in an async fashion). As a result, any exceptions that occur
     * while sending the message are thrown by this method (which can be useful to test the configuration of this
     * service).
     *
     * @param message   The body of the message (cannot be null or empty).
     * @param recipient The address / phone number to which the message is to be send (cannot be null or empty).
     * @throws Exception On any problem.
     */
    public void sendImmediately( String message, String recipient ) throws Exception
    {
        if ( message == null || message.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'message' cannot be null or an empty String." );
        }

        if ( recipient == null || recipient.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'recipient' cannot be null or an empty String." );
        }

        try
        {
            new SmsTask( sessionPool, message, recipient ).sendMessage();
        }
        catch ( Exception e )
        {
            Log.error( "An exception occurred while sending a SMS message (to '{}')", recipient, e );
            throw e;
        }
    }

    /**
     * Checks if an exception in the chain of the provided throwable contains a 'command status' that can be
     * translated in a somewhat more helpful error message.
     *
     * The list of error messages was taken from http://www.smssolutions.net/tutorials/smpp/smpperrorcodes/
     *
     * @param ex The exception in which to search for a command status.
     * @return a human readable error message.
     */
    public static String getDescriptiveMessage( Throwable ex )
    {
        if ( ex instanceof NegativeResponseException )
        {
            final Map<Integer, String> errors = new HashMap<>();
            errors.put( 0x00000000, "No Error" );
            errors.put( 0x00000001, "Message too long" );
            errors.put( 0x00000002, "Command length is invalid" );
            errors.put( 0x00000003, "Command ID is invalid or not supported" );
            errors.put( 0x00000004, "Incorrect bind status for given command" );
            errors.put( 0x00000005, "Already bound" );
            errors.put( 0x00000006, "Invalid Priority Flag" );
            errors.put( 0x00000007, "Invalid registered delivery flag" );
            errors.put( 0x00000008, "System error" );
            errors.put( 0x0000000A, "Invalid source address" );
            errors.put( 0x0000000B, "Invalid destination address" );
            errors.put( 0x0000000C, "Message ID is invalid" );
            errors.put( 0x0000000D, "Bind failed" );
            errors.put( 0x0000000E, "Invalid password" );
            errors.put( 0x0000000F, "Invalid System ID" );
            errors.put( 0x00000011, "Cancelling message failed" );
            errors.put( 0x00000013, "Message recplacement failed" );
            errors.put( 0x00000014, "Message queue full" );
            errors.put( 0x00000015, "Invalid service type" );
            errors.put( 0x00000033, "Invalid number of destinations" );
            errors.put( 0x00000034, "Invalid distribution list name" );
            errors.put( 0x00000040, "Invalid destination flag" );
            errors.put( 0x00000042, "Invalid submit with replace request" );
            errors.put( 0x00000043, "Invalid esm class set" );
            errors.put( 0x00000044, "Invalid submit to ditribution list" );
            errors.put( 0x00000045, "Submitting message has failed" );
            errors.put( 0x00000048, "Invalid source address type of number ( TON )" );
            errors.put( 0x00000049, "Invalid source address numbering plan ( NPI )" );
            errors.put( 0x00000050, "Invalid destination address type of number ( TON )" );
            errors.put( 0x00000051, "Invalid destination address numbering plan ( NPI )" );
            errors.put( 0x00000053, "Invalid system type" );
            errors.put( 0x00000054, "Invalid replace_if_present flag" );
            errors.put( 0x00000055, "Invalid number of messages" );
            errors.put( 0x00000058, "Throttling error" );
            errors.put( 0x00000061, "Invalid scheduled delivery time" );
            errors.put( 0x00000062, "Invalid Validty Period value" );
            errors.put( 0x00000063, "Predefined message not found" );
            errors.put( 0x00000064, "ESME Receiver temporary error" );
            errors.put( 0x00000065, "ESME Receiver permanent error" );
            errors.put( 0x00000066, "ESME Receiver reject message error" );
            errors.put( 0x00000067, "Message query request failed" );
            errors.put( 0x000000C0, "Error in the optional part of the PDU body" );
            errors.put( 0x000000C1, "TLV not allowed" );
            errors.put( 0x000000C2, "Invalid parameter length" );
            errors.put( 0x000000C3, "Expected TLV missing" );
            errors.put( 0x000000C4, "Invalid TLV value" );
            errors.put( 0x000000FE, "Transaction delivery failure" );
            errors.put( 0x000000FF, "Unknown error" );
            errors.put( 0x00000100, "ESME not authorised to use specified servicetype" );
            errors.put( 0x00000101, "ESME prohibited from using specified operation" );
            errors.put( 0x00000102, "Specified servicetype is unavailable" );
            errors.put( 0x00000103, "Specified servicetype is denied" );
            errors.put( 0x00000104, "Invalid data coding scheme" );
            errors.put( 0x00000105, "Invalid source address subunit" );
            errors.put( 0x00000106, "Invalid destination address subunit" );
            errors.put( 0x0000040B, "Insufficient credits to send message" );
            errors.put( 0x0000040C, "Destination address blocked by the ActiveXperts SMPP Demo Server" );

            String error = errors.get( ( (NegativeResponseException) ex ).getCommandStatus() );
            if ( ex.getMessage() != null && !ex.getMessage().isEmpty() )
            {
                error += " (exception message: '" + ex.getMessage() + "')";
            }
            return error;
        }
        else if ( ex.getCause() != null )
        {
            return getDescriptiveMessage( ex.getCause() );
        }

        return ex.getMessage();
    }

    /**
     * Runnable that allows an SMS to be sent in a different thread.
     */
    private static class SmsTask implements Runnable
    {
        private final ObjectPool<SMPPSession> sessionPool;

        // Settings that apply to source of an SMS message.
        private final TypeOfNumber sourceTon = JiveGlobals.getEnumProperty( "sms.smpp.source.ton", TypeOfNumber.class, TypeOfNumber.UNKNOWN );
        private final NumberingPlanIndicator sourceNpi = JiveGlobals.getEnumProperty( "sms.smpp.source.npi", NumberingPlanIndicator.class, NumberingPlanIndicator.UNKNOWN );
        private final String sourceAddress = JiveGlobals.getProperty( "sms.smpp.source.address" );

        // Settings that apply to destination of an SMS message.
        private final TypeOfNumber destinationTon = JiveGlobals.getEnumProperty( "sms.smpp.destination.ton", TypeOfNumber.class, TypeOfNumber.UNKNOWN );
        private final NumberingPlanIndicator destinationNpi = JiveGlobals.getEnumProperty( "sms.smpp.destination.npi", NumberingPlanIndicator.class, NumberingPlanIndicator.UNKNOWN );

        private final String destinationAddress;
        private final byte[] message;

        // Non-configurable defaults (for now - TODO?)
        private final ESMClass esm = new ESMClass();
        private final byte protocolId = 0;
        private final byte priorityFlag = 1;
        private final String serviceType = "CMT";
        private final String scheduleDeliveryTime = timeFormatter.format( new Date() );
        private final String validityPeriod = null;
        private final RegisteredDelivery registeredDelivery = new RegisteredDelivery( SMSCDeliveryReceipt.DEFAULT );
        private final byte replaceIfPresentFlag = 0;
        private final DataCoding dataCoding = new GeneralDataCoding( Alphabet.ALPHA_DEFAULT, MessageClass.CLASS1, false );
        private final byte smDefaultMsgId = 0;


        SmsTask( ObjectPool<SMPPSession> sessionPool, String message, String destinationAddress )
        {
            this.sessionPool = sessionPool;
            this.message = message.getBytes();
            this.destinationAddress = destinationAddress;
        }

        @Override
        public void run()
        {
            try
            {
                sendMessage();
            }
            catch ( Exception e )
            {
                Log.error( "An exception occurred while sending a SMS message (to '{}')", destinationAddress, e );
            }
        }

        public void sendMessage() throws Exception
        {
            final SMPPSession session = sessionPool.borrowObject();
            try
            {
                final String messageId = session.submitShortMessage(
                    serviceType,
                    sourceTon, sourceNpi, sourceAddress,
                    destinationTon, destinationNpi, destinationAddress,
                    esm, protocolId, priorityFlag,
                    scheduleDeliveryTime, validityPeriod, registeredDelivery, replaceIfPresentFlag,
                    dataCoding, smDefaultMsgId, message );
                Log.debug( "Message submitted, message_id is '{}'.", messageId );
            }
            finally
            {
                sessionPool.returnObject( session );
            }
        }
    }

    /**
     * A factory of SMPPSession instances that are used in an object pool.
     *
     * @author Guus der Kinderen, guus.der.kinderen@gmail.com
     */
    private static class SMPPSessionFactory extends BasePooledObjectFactory<SMPPSession>
    {
        private static final Logger Log = LoggerFactory.getLogger( SMPPSessionFactory.class );

        @Override
        public SMPPSession create() throws Exception
        {
            // SMSC connection settings
            final String host = JiveGlobals.getProperty( "sms.smpp.host", "localhost" );
            final int port = JiveGlobals.getIntProperty( "sms.smpp.port", 2775 );
            final String systemId = JiveGlobals.getProperty( "sms.smpp.systemId" );
            final String password = JiveGlobals.getProperty( "sms.smpp.password" );
            final String systemType = JiveGlobals.getProperty( "sms.smpp.systemType" );

            // Settings that apply to 'receiving' SMS. Should not apply to this implementation, as we're not receiving anything..
            final TypeOfNumber receiveTon = JiveGlobals.getEnumProperty( "sms.smpp.receive.ton", TypeOfNumber.class, TypeOfNumber.UNKNOWN );
            final NumberingPlanIndicator receiveNpi = JiveGlobals.getEnumProperty( "sms.smpp.receive.npi", NumberingPlanIndicator.class, NumberingPlanIndicator.UNKNOWN );

            Log.debug( "Creating a new sesssion (host: '{}', port: '{}', systemId: '{}'.", host, port, systemId );
            final SMPPSession session = new SMPPSession();
            session.connectAndBind( host, port, new BindParameter( BindType.BIND_TX, systemId, password, systemType, receiveTon, receiveNpi, null ) );
            Log.debug( "Created a new session with ID '{}'.", session.getSessionId() );
            return session;
        }

        @Override
        public boolean validateObject( PooledObject<SMPPSession> pooledObject )
        {
            final SMPPSession session = pooledObject.getObject();
            final boolean isValid = session.getSessionState().isTransmittable(); // updated by the SMPPSession internal enquireLink timer.
            Log.debug( "Ran a check to see if session with ID '{}' is valid. Outcome: {}", session.getSessionId(), isValid );
            return isValid;
        }

        @Override
        public void destroyObject( PooledObject<SMPPSession> pooledObject ) throws Exception
        {
            final SMPPSession session = pooledObject.getObject();
            Log.debug( "Destroying a pooled session with ID '{}'.", session.getSessionId() );
            session.unbindAndClose();
        }

        @Override
        public PooledObject<SMPPSession> wrap( SMPPSession smppSession )
        {
            return new DefaultPooledObject<>( smppSession );
        }
    }

    /**
     * Implementation of an Object pool that manages instances of SMPPSession. The intend of this pool is to have a
     * single session, that's allowed to be idle for at least two minutes before being closed.
     *
     * The pool reacts to Openfire property changes, clearing all (inactive) sessions when a property used to create
     * a session is modified. Note that sessions that are borrowed from the pool are not affected by such a change. When
     * a property change occurs while a session is borrowed, a warning is logged (the property change will be applied
     * when that session is eventually rotated out of the pool by the eviction strategy.
     *
     * @author Guus der Kinderen, guus.der.kinderen@gmail.com
     */
    private static class SMPPSessionPool extends GenericObjectPool<SMPPSession> implements PropertyEventListener
    {
        private static final Logger Log = LoggerFactory.getLogger( SMPPSessionPool.class );

        SMPPSessionPool()
        {
            super( new SMPPSessionFactory() );
            setMaxTotal( JiveGlobals.getIntProperty( "sms.smpp.connections.maxAmount", 1 ) );
            setNumTestsPerEvictionRun( getMaxTotal() );

            setMinEvictableIdleTimeMillis( JiveGlobals.getLongProperty( "sms.smpp.connections.idleMillis", 1000 * 60 * 2 ) );
            if ( getMinEvictableIdleTimeMillis() > 0 )
            {
                setTimeBetweenEvictionRunsMillis( getMinEvictableIdleTimeMillis() / 10 );
            }

            setTestOnBorrow( true );
            setTestWhileIdle( true );
        }

        void processPropertyChange( String propertyName )
        {
            final Set<String> ofInterest = new HashSet<>();
            ofInterest.add( "sms.smpp.host" );
            ofInterest.add( "sms.smpp.port" );
            ofInterest.add( "sms.smpp.systemId" );
            ofInterest.add( "sms.smpp.password" );
            ofInterest.add( "sms.smpp.systemType" );
            ofInterest.add( "sms.smpp.receive.ton" );
            ofInterest.add( "sms.smpp.receive.npi" );

            if ( ofInterest.contains( propertyName ) )
            {
                Log.debug( "Property change for '{}' detected. Clearing all (inactive) sessions.", propertyName );
                if ( getNumActive() > 0 )
                {
                    // This can occur when an SMS is being sent while the property is being updated at the same time.
                    Log.warn( "Note that property change for '{}' will not affect one or more sessions that are currently actively used (although changes will be applied after the session is rotated out, due to time-based eviction).", propertyName );
                }
                clear();
            }

            // No need to clear the sessions for these properties:
            if ( propertyName.equals( "sms.smpp.connections.maxAmount" ) )
            {
                setMaxTotal( JiveGlobals.getIntProperty( "sms.smpp.connections.maxAmount", 1 ) );
                setNumTestsPerEvictionRun( getMaxTotal() );
            }

            if ( propertyName.equals( "sms.smpp.connections.idleMillis" ) )
            {
                setMinEvictableIdleTimeMillis( JiveGlobals.getLongProperty( "sms.smpp.connections.idleMillis", 1000 * 60 * 2 ) );
                if ( getMinEvictableIdleTimeMillis() > 0 )
                {
                    setTimeBetweenEvictionRunsMillis( getMinEvictableIdleTimeMillis() / 10 );
                }
            }
        }

        @Override
        public void propertySet( String property, Map<String, Object> params )
        {
            processPropertyChange( property );
        }

        @Override
        public void propertyDeleted( String property, Map<String, Object> params )
        {
            processPropertyChange( property );
        }

        @Override
        public void xmlPropertySet( String property, Map<String, Object> params )
        {
            processPropertyChange( property );
        }

        @Override
        public void xmlPropertyDeleted( String property, Map<String, Object> params )
        {
            processPropertyChange( property );
        }
    }
}
