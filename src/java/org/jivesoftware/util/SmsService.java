package org.jivesoftware.util;

import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.*;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.TimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A service to send SMS messages.<p>
 *
 * This class is configured with a set of Jive properties. Note that each service provider can require a different set
 * of properties to be set.
 * <ul>
 * <li><tt>sms.smpp.host</tt> -- the host name of your SMPP Server or SMSC, i.e. smsc.example.org. The default value is "localhost".
 * <li><tt>sms.smpp.port</tt> -- the port on which the SMSC is listening. Defaults to 2775.
 * <li><tt>sms.smpp.systemId</tt> -- the 'user name' to use when connecting to the SMSC.
 * <li><tt>sms.smpp.password</tt> -- the password that authenticates the systemId value when connecting to the SMSC.
 * <li><tt>sms.smpp.systemType</tt> -- an optional system type, which, if defined, will be used when connecting to the SMSC.
 * <li><tt>sms.smpp.receive.ton</tt> -- The type-of-number value for 'receiving' SMS messages. Defaults to 'UNKNOWN'.
 * <li><tt>sms.smpp.receive.npi</tt> -- The number-plan-indicator value for 'receiving' SMS messages. Defaults to 'UNKNOWN'.
 * <li><tt>sms.smpp.source.ton</tt> -- The type-of-number value for the source of SMS messages. Defaults to 'UNKNOWN'.
 * <li><tt>sms.smpp.source.npi</tt> -- The number-plan-indicator value for the source of SMS messages. Defaults to 'UNKNOWN'.
 * <li><tt>sms.smpp.source.address</tt> -- The source address of SMS messages.
 * <li><tt>sms.smpp.destination.ton</tt> -- The type-of-number value for the destination of SMS messages. Defaults to 'UNKNOWN'.
 * <li><tt>sms.smpp.destination.npi</tt> -- The number-plan-indicator value for the destination of SMS messages. Defaults to 'UNKNOWN'.
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
     * Causes a new SMS message to be sent.
     *
     * Note that the message is sent asynchronously. This method does not block. A successful invocation does not
     * guarantee successful delivery
     *
     * @param message The body of the message (cannot be null or empty).
     * @param recipient The address / phone number to which the message is to be send (cannot be null or empty).
     */
    public void send( String message, String recipient )
    {
        if ( message == null || message.isEmpty() ) {
            throw new IllegalArgumentException( "Argument 'message' cannot be null or an empty String." );
        }

        if ( recipient == null || recipient.isEmpty() ) {
            throw new IllegalArgumentException( "Argument 'recipient' cannot be null or an empty String." );
        }

        TaskEngine.getInstance().submit(new SmsTask( message, recipient ) );
    }

    /**
     * Causes a new SMS message to be sent.
     *
     * This method differs from {@link #send(String, String)} in that the message is sent before this method returns,
     * rather than queueing the messages to be sent later (in an async fashion). As a result, any exceptions that occur
     * while sending the message are thrown by this method (which can be useful to test the configuration of this
     * service).
     *
     * @param message The body of the message (cannot be null or empty).
     * @param recipient The address / phone number to which the message is to be send (cannot be null or empty).
     * @throws PDUException
     * @throws ResponseTimeoutException
     * @throws InvalidResponseException
     * @throws NegativeResponseException
     * @throws IOException
     */
    public void sendImmediately( String message, String recipient ) throws PDUException, ResponseTimeoutException, InvalidResponseException, NegativeResponseException, IOException
    {
        if ( message == null || message.isEmpty() ) {
            throw new IllegalArgumentException( "Argument 'message' cannot be null or an empty String." );
        }

        if ( recipient == null || recipient.isEmpty() ) {
            throw new IllegalArgumentException( "Argument 'recipient' cannot be null or an empty String." );
        }

        try
        {
            new SmsTask( message, recipient ).sendMessage();
        }
        catch ( PDUException | ResponseTimeoutException | InvalidResponseException | NegativeResponseException | IOException e )
        {
            Log.error( "An exception occurred while sending a SMS message (to '{}')", recipient, e);
            throw e;
        }
    }

    /**
     * Checks if an exception in the chain of the provided throwable contains a 'command status' that can be
     * translated in a somewhat more helpful error message.
     *
     * The list of error messages was taken from http://www.smssolutions.net/tutorials/smpp/smpperrorcodes/
     * @param ex The exception in which to search for a command status.
     * @return
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
        // SMSC connection settings
        private final String host       = JiveGlobals.getProperty(    "sms.smpp.host",      "localhost" );
        private final int port          = JiveGlobals.getIntProperty( "sms.smpp.port",      2775        );
        private final String systemId   = JiveGlobals.getProperty(    "sms.smpp.systemId"               );
        private final String password   = JiveGlobals.getProperty(    "sms.smpp.password"               );
        private final String systemType = JiveGlobals.getProperty(    "sms.smpp.systemType"             );

        // Settings that apply to 'receiving' SMS. Should not apply to this implementation, as we're not receiving anything..
        private final TypeOfNumber receiveTon           = JiveGlobals.getEnumProperty( "sms.smpp.receive.ton", TypeOfNumber.class, TypeOfNumber.UNKNOWN );
        private final NumberingPlanIndicator receiveNpi = JiveGlobals.getEnumProperty( "sms.smpp.receive.npi", NumberingPlanIndicator.class, NumberingPlanIndicator.UNKNOWN );

        // Settings that apply to source of an SMS message.
        private final TypeOfNumber sourceTon           = JiveGlobals.getEnumProperty( "sms.smpp.source.ton",   TypeOfNumber.class, TypeOfNumber.UNKNOWN );
        private final NumberingPlanIndicator sourceNpi = JiveGlobals.getEnumProperty( "sms.smpp.source.npi",   NumberingPlanIndicator.class, NumberingPlanIndicator.UNKNOWN );
        private final String sourceAddress             = JiveGlobals.getProperty(     "sms.smpp.source.address" );

        // Settings that apply to destination of an SMS message.
        private final TypeOfNumber destinationTon           = JiveGlobals.getEnumProperty( "sms.smpp.destination.ton", TypeOfNumber.class, TypeOfNumber.UNKNOWN );
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


        SmsTask( String message, String destinationAddress )
        {
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
            catch ( PDUException | ResponseTimeoutException | InvalidResponseException | NegativeResponseException | IOException e )
            {
                Log.error( "An exception occurred while sending a SMS message (to '{}')", destinationAddress, e);
            }
        }

        public void sendMessage() throws IOException, PDUException, InvalidResponseException, NegativeResponseException, ResponseTimeoutException
        {
            final SMPPSession session = new SMPPSession();
            try
            {
                session.connectAndBind( host, port, new BindParameter( BindType.BIND_TX, systemId, password, systemType, receiveTon, receiveNpi, null ) );

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
                session.unbindAndClose();
            }
        }
    }
}
