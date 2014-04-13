/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import javax.media.*;
import javax.media.protocol.*;

import org.ice4j.socket.*;
import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.packetlogging.*;
import org.jitsi.util.*;

import org.jitsi.videobridge.openfire.PluginImpl.Participant;

/**
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public abstract class RTPConnectorInputStream
    implements PushSourceStream,
               Runnable
{
    /**
     * The value of the property <tt>controls</tt> of
     * <tt>RTPConnectorInputStream</tt> when there are no controls. Explicitly
     * defined in order to reduce unnecessary allocations.
     */
    private static final Object[] EMPTY_CONTROLS = new Object[0];

    /**
     * The length in bytes of the buffers of <tt>RTPConnectorInputStream</tt>
     * receiving packets from the network.
     */
    public static final int PACKET_RECEIVE_BUFFER_LENGTH = 4 * 1024;

    /**
     * The <tt>Logger</tt> used by the <tt>RTPConnectorInputStream</tt> class
     * and its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(RTPConnectorInputStream.class);

    /**
     * Packet receive buffer
     */
    private final byte[] buffer = new byte[PACKET_RECEIVE_BUFFER_LENGTH];

    /**
     * Whether this stream is closed. Used to control the termination of worker
     * thread.
     */
    protected boolean closed;

    public Participant videoRecorder;
    public Participant audioScanner;

    /**
     * The <tt>DatagramPacketFilter</tt>s which allow dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     */
    private DatagramPacketFilter[] datagramPacketFilters;

    /**
     * Caught an IO exception during read from socket
     */
    protected boolean ioError = false;

    /**
     * The packet data to be read out of this instance through its
     * {@link #read(byte[], int, int)} method.
     */
    private RawPacket pkt;

    /**
     * The <tt>Object</tt> which synchronizes the access to {@link #pkt}.
     */
    private final Object pktSyncRoot = new Object();

    /**
     * The adapter of this <tt>PushSourceStream</tt> to the
     * <tt>PushBufferStream</tt> interface.
     */
    private final PushBufferStream pushBufferStream;

    /**
     * The pool of <tt>RawPacket[]</tt> instances to reduce their allocations
     * and garbage collection. Contains arrays full of <tt>null</tt>.
     */
    private final Queue<RawPacket[]> rawPacketArrayPool
        = new LinkedBlockingQueue<RawPacket[]>();

    /**
     * The pool of <tt>RawPacket</tt> instances to reduce their allocations and
     * garbage collection.
     */
    private final Queue<RawPacket> rawPacketPool
        = new LinkedBlockingQueue<RawPacket>();

    /**
     * The Thread receiving packets.
     */
    protected Thread receiverThread = null;

    /**
     * SourceTransferHandler object which is used to read packets.
     */
    private SourceTransferHandler transferHandler;

    /**
     * Whether this <tt>RTPConnectorInputStream</tt> is enabled or disabled.
     * While disabled, the stream does not accept any packets.
     */
    private boolean enabled = true;

    /**
     * Initializes a new <tt>RTPConnectorInputStream</tt> which is to receive
     * packet data from a specific UDP socket.
     */
    public RTPConnectorInputStream()
    {
        // PacketLoggingService
        addDatagramPacketFilter(
                new DatagramPacketFilter()
                {
                    /**
                     * Used for debugging. As we don't log every packet, we must
                     * count them and decide which to log.
                     */
                    private long numberOfPackets = 0;

                    public boolean accept(DatagramPacket p)
                    {
                        numberOfPackets++;
                        if (RTPConnectorOutputStream.logPacket(numberOfPackets))
                        {
                            PacketLoggingService packetLogging
                                = LibJitsi.getPacketLoggingService();

                            if ((packetLogging != null)
                                    && packetLogging.isLoggingEnabled(
                                            PacketLoggingService.ProtocolName
                                                    .RTP))
                                doLogPacket(p);
                        }

                        return true;
                    }
                });

        /*
         * Adapt this PushSourceStream to the PushBufferStream interface in
         * order to make it possible to read the Buffer flags of RawPacket.
         */
        pushBufferStream
            = new PushBufferStreamAdapter(this, null)
            {
                @Override
                protected int doRead(
                        Buffer buffer,
                        byte[] data, int offset, int length)
                    throws IOException
                {
                    return
                        RTPConnectorInputStream.this.read(
                                buffer,
                                data, offset, length);
                }
            };
    }

    /**
     * Close this stream, stops the worker thread.
     */
    public synchronized void close()
    {
    }

    /**
     * Creates a new <tt>RawPacket</tt> from a specific <tt>DatagramPacket</tt>
     * in order to have this instance receive its packet data through its
     * {@link #read(byte[], int, int)} method. Returns an array of
     * <tt>RawPacket</tt> with the created packet as its first element (and
     * <tt>null</tt> for the other elements).
     *
     * Allows extenders to intercept the packet data and possibly filter and/or
     * modify it.
     *
     * @param datagramPacket the <tt>DatagramPacket</tt> containing the packet
     * data
     * @return an array of <tt>RawPacket</tt> containing the <tt>RawPacket</tt>
     * which contains the packet data of the
     * specified <tt>DatagramPacket</tt> as its first element.
     */
    protected RawPacket[] createRawPacket(DatagramPacket datagramPacket)
    {
        RawPacket[] pkts = rawPacketArrayPool.poll();
        if (pkts == null)
            pkts = new RawPacket[1];

        RawPacket pkt = rawPacketPool.poll();
        if (pkt == null)
            pkt = new RawPacket();

        pkt.setBuffer(datagramPacket.getData());
        pkt.setFlags(0);
        pkt.setLength(datagramPacket.getLength());
        pkt.setOffset(datagramPacket.getOffset());

        pkts[0] = pkt;
        return pkts;
    }

    /**
     * Provides a dummy implementation to {@link
     * RTPConnectorInputStream#endOfStream()} that always returns
     * <tt>false</tt>.
     *
     * @return <tt>false</tt>, no matter what.
     */
    public boolean endOfStream()
    {
        return false;
    }

    /**
     * Provides a dummy implementation to {@link
     * RTPConnectorInputStream#getContentDescriptor()} that always returns
     * <tt>null</tt>.
     *
     * @return <tt>null</tt>, no matter what.
     */
    public ContentDescriptor getContentDescriptor()
    {
        return null;
    }

    /**
     * Provides a dummy implementation to {@link
     * RTPConnectorInputStream#getContentLength()} that always returns
     * <tt>LENGTH_UNKNOWN</tt>.
     *
     * @return <tt>LENGTH_UNKNOWN</tt>, no matter what.
     */
    public long getContentLength()
    {
        return LENGTH_UNKNOWN;
    }

    /**
     * Provides a dummy implementation of
     * {@link RTPConnectorInputStream#getControl(String)} that always returns
     * <tt>null</tt>.
     *
     * @param controlType ignored.
     * @return <tt>null</tt>, no matter what.
     */
    public Object getControl(String controlType)
    {
        if (PushBufferStream.class.getName().equals(controlType))
            return pushBufferStream;
        else
            return null;
    }

    /**
     * Provides a dummy implementation of
     * {@link RTPConnectorInputStream#getControls()} that always returns
     * <tt>EMPTY_CONTROLS</tt>.
     *
     * @return <tt>EMPTY_CONTROLS</tt>, no matter what.
     */
    public Object[] getControls()
    {
        return EMPTY_CONTROLS;
    }

    /**
     * Provides a dummy implementation to {@link
     * RTPConnectorInputStream#getMinimumTransferSize()} that always returns
     * <tt>2 * 1024</tt>.
     *
     * @return <tt>2 * 1024</tt>, no matter what.
     */
    public int getMinimumTransferSize()
    {
        return 2 * 1024; // twice the MTU size, just to be safe.
    }

    /**
     * Pools the specified <tt>RawPacket</tt> in order to avoid future
     * allocations and to reduce the effects of garbage collection.
     *
     * @param pkt the <tt>RawPacket</tt> to be offered to {@link #rawPacketPool}
     */
    private void poolRawPacket(RawPacket pkt)
    {
        pkt.setBuffer(null);
        pkt.setFlags(0);
        pkt.setLength(0);
        pkt.setOffset(0);
        rawPacketPool.offer(pkt);
    }

    /**
     * Copies the content of the most recently received packet into
     * <tt>buffer</tt>.
     *
     * @param buffer the <tt>byte[]</tt> that we'd like to copy the content of
     * the packet to.
     * @param offset the position where we are supposed to start writing in
     * <tt>buffer</tt>.
     * @param length the number of <tt>byte</tt>s available for writing in
     * <tt>buffer</tt>.
     * @return the number of bytes read
     * @throws IOException if <tt>length</tt> is less than the size of the
     * packet.
     */
    public int read(byte[] buffer, int offset, int length)
            throws IOException
    {
        return read(null, buffer, offset, length);
    }

    /**
     * Copies the content of the most recently received packet into
     * <tt>data</tt>.
     *
     * @param buffer an optional <tt>Buffer</tt> instance associated with the
     * specified <tt>data</tt>, <tt>offset</tt> and <tt>length</tt> and
     * provided to the method in case the implementation would like to provide
     * additional <tt>Buffer</tt> properties such as <tt>flags</tt>
     * @param data the <tt>byte[]</tt> that we'd like to copy the content of
     * the packet to.
     * @param offset the position where we are supposed to start writing in
     * <tt>data</tt>.
     * @param length the number of <tt>byte</tt>s available for writing in
     * <tt>data</tt>.
     * @return the number of bytes read
     * @throws IOException if <tt>length</tt> is less than the size of the
     * packet.
     */
    protected int read(Buffer buffer, byte[] data, int offset, int length)
        throws IOException
    {
        if (data == null)
            throw new NullPointerException("data");

        if (ioError)
            return -1;

        RawPacket pkt;

        synchronized (pktSyncRoot)
        {
            pkt = this.pkt;
            this.pkt = null;
        }

        int pktLength;

        if (pkt == null)
        {
            pktLength = 0;
        }
        else
        {
            // By default, pkt will be returned to the pool after it was read.
            boolean poolPkt = true;

            try
            {
                pktLength = pkt.getLength();
                if (length < pktLength)
                {
                    /*
                     * If pkt is still the latest RawPacket made available to
                     * reading, reinstate it for the next invocation of read;
                     * otherwise, return it to the pool.
                     */
                    poolPkt = false;
                    throw new IOException(
                            "Input buffer not big enough for " + pktLength);
                }
                else
                {
                    byte[] pktBuffer = pkt.getBuffer();

                    if (pktBuffer == null)
                    {
                        throw new NullPointerException(
                                "pkt.buffer null, pkt.length " + pktLength
                                    + ", pkt.offset " + pkt.getOffset());
                    }
                    else
                    {
                        System.arraycopy(
                                pkt.getBuffer(), pkt.getOffset(),
                                data, offset,
                                pktLength);
                        if (buffer != null)
                            buffer.setFlags(pkt.getFlags());

                        if (videoRecorder != null) videoRecorder.recordData(pkt);
                        if (audioScanner != null) audioScanner.scanData(pkt);

                    }
                }
            }
            finally
            {
                if (!poolPkt)
                {
                    synchronized (pktSyncRoot)
                    {
                        if (this.pkt == null)
                            this.pkt = pkt;
                        else
                            poolPkt = true;
                    }
                }
                if (poolPkt)
                {
                    // Return pkt to the pool because it was successfully read.
                    poolRawPacket(pkt);
                }
            }
        }

        return pktLength;
    }

    /**
     * Log the packet.
     *
     * @param packet packet to log
     */
    protected abstract void doLogPacket(DatagramPacket packet);

    /**
     * Receive packet.
     *
     * @param p packet for receiving
     * @throws IOException if something goes wrong during receiving
     */
    protected abstract void receivePacket(DatagramPacket p)
        throws IOException;

    /**
     * Listens for incoming datagrams, stores them for reading by the
     * <tt>read</tt> method and notifies the local <tt>transferHandler</tt>
     * that there's data to be read.
     */
    public void run()
    {
        DatagramPacket p
            = new DatagramPacket(buffer, 0, PACKET_RECEIVE_BUFFER_LENGTH);

        while (!closed)
        {
            try
            {
                // http://code.google.com/p/android/issues/detail?id=24765
                if (OSUtils.IS_ANDROID)
                    p.setLength(PACKET_RECEIVE_BUFFER_LENGTH);

                receivePacket(p);
            }
            catch (IOException e)
            {
                ioError = true;
                break;
            }

            /*
             * Do the DatagramPacketFilters accept the received DatagramPacket?
             */
            DatagramPacketFilter[] datagramPacketFilters
                = getDatagramPacketFilters();
            boolean accept;

            if (!enabled)
                accept = false;
            else if (datagramPacketFilters == null)
                accept = true;
            else
            {
                accept = true;
                for (int i = 0; i < datagramPacketFilters.length; i++)
                {
                    try
                    {
                        if (!datagramPacketFilters[i].accept(p))
                        {
                            accept = false;
                            break;
                        }
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                    }
                }
            }

            if (accept)
            {
                RawPacket pkts[] = createRawPacket(p);

                for (int i = 0; i < pkts.length; i++)
                {
                    RawPacket pkt = pkts[i];

                    pkts[i] = null;

                    if (pkt != null)
                    {
                        if (pkt.isInvalid())
                        {
                            /*
                             * Return pkt to the pool because it is invalid and,
                             * consequently, will not be made available to
                             * reading.
                             */
                            poolRawPacket(pkt);
                        }
                        else
                        {
                            RawPacket oldPkt;

                            synchronized (pktSyncRoot)
                            {
                                oldPkt = this.pkt;
                                this.pkt = pkt;
                            }
                            if (oldPkt != null)
                            {
                                /*
                                 * Return oldPkt to the pool because it was made
                                 * available to reading and it was not read.
                                 */
                                poolRawPacket(oldPkt);
                            }

                            if ((transferHandler != null) && !closed)
                            {
                                try
                                {
                                    transferHandler.transferData(this);
                                }
                                catch (Throwable t)
                                {
                                    /*
                                     * XXX We cannot allow transferHandler to
                                     * kill us.
                                     */
                                    if (t instanceof ThreadDeath)
                                    {
                                        throw (ThreadDeath) t;
                                    }
                                    else
                                    {
                                        logger.warn(
                                            "An RTP packet may have not been"
                                                + " fully handled.",
                                            t);
                                    }
                                }
                            }
                        }
                    }
                }
                rawPacketArrayPool.offer(pkts);
            }
        }
    }

    /**
     * Sets the <tt>transferHandler</tt> that this connector should be notifying
     * when new data is available for reading.
     *
     * @param transferHandler the <tt>transferHandler</tt> that this connector
     * should be notifying when new data is available for reading.
     */
    public void setTransferHandler(SourceTransferHandler transferHandler)
    {
        if (!closed)
            this.transferHandler = transferHandler;
    }

    /**
     * Changes current thread priority.
     * @param priority the new priority.
     */
    public void setPriority(int priority)
    {
        // currently no priority is set
//        if (receiverThread != null)
//            receiverThread.setPriority(priority);
    }

    /**
     * Gets the <tt>DatagramPacketFilter</tt>s which allow dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     *
     * @return the <tt>DatagramPacketFilter</tt>s which allow dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     */
    public synchronized DatagramPacketFilter[] getDatagramPacketFilters()
    {
        return datagramPacketFilters;
    }

    /**
     * Adds a <tt>DatagramPacketFilter</tt> which allows dropping
     * <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s.
     *
     * @param datagramPacketFilter the <tt>DatagramPacketFilter</tt> which
     * allows dropping <tt>DatagramPacket</tt>s before they are converted into
     * <tt>RawPacket</tt>s
     */
    public synchronized void addDatagramPacketFilter(
            DatagramPacketFilter datagramPacketFilter)
    {
        if (datagramPacketFilter == null)
            throw new NullPointerException("datagramPacketFilter");

        if (datagramPacketFilters == null)
        {
            datagramPacketFilters
                = new DatagramPacketFilter[] { datagramPacketFilter };
        }
        else
        {
            final int length = datagramPacketFilters.length;

            for (int i = 0; i < length; i++)
                if (datagramPacketFilter.equals(datagramPacketFilters[i]))
                    return;

            DatagramPacketFilter[] newDatagramPacketFilters
                = new DatagramPacketFilter[length + 1];

            System.arraycopy(
                    datagramPacketFilters, 0,
                    newDatagramPacketFilters, 0,
                    length);
            newDatagramPacketFilters[length] = datagramPacketFilter;
            datagramPacketFilters = newDatagramPacketFilters;
        }
    }

    /**
     * Enables or disables this <tt>RTPConnectorInputStream</tt>.
     * While the stream is disabled, it does not accept any packets.
     *
     * @param enabled <tt>true</tt> to enable, <tt>false</tt> to disable.
     */
    public void setEnabled(boolean enabled)
    {
        if (logger.isDebugEnabled())
            logger.debug("setEnabled: " + enabled);

        this.enabled = enabled;
    }
}
