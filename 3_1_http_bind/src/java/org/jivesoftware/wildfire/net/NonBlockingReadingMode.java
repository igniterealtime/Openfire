/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZInputStream;
import org.dom4j.Element;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.SessionManager;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

/**
 * Process incoming packets using a non-blocking model.
 *
 * @author Daniele Piras
 */
class NonBlockingReadingMode extends SocketReadingMode {

    // DANIELE: Socket read timeout in milliseconds
    private static int READ_TIMEOUT = 0;

    private static String STREAM_START = "<stream:stream";

    // DANIELE: Semaphore to avoid concurrent reading operation from different thread
    private boolean isReading;

    // DANIELE: lightweight xml parser.
    private XMLLightweightParser xmlLightWeightParser;

    // DANIELE: Channel for socket connection
    private SocketChannel socketChannel;

    // DANIELE: Indicate if the reading operations has been scheduled into the executor.
    // this is very important because if all reading thread are busy is used to avoid
    // to reinsert into the queue the reading operation.
    private boolean isScheduled = false;

    // DANIELE: Indicate if a session is already created
    private boolean sessionCreated = false;

    // DANIELE: Indicate if a stream:stream is arrived to complete a sals authentication
    private boolean awaytingSasl = false;

    // DANIELE: Indicate if a stream:stream is arrived to complete compression
    private boolean awaitingForCompleteCompression = false;

    private StreamReader streamReader;

    public NonBlockingReadingMode(Socket socket, SocketReader socketReader) {
        super(socket, socketReader);
        // DANIELE: Initialization
        // Setting timeout for reading operations.
        try {
            socket.setSoTimeout(READ_TIMEOUT);
        }
        catch (SocketException e) {
            // There is an exception...
            Log.warn(e);
        }

        socketChannel = socket.getChannel();

        // Initialize XML light weight parser
        xmlLightWeightParser = new XMLLightweightParser(socketChannel, CHARSET);


        isReading = false;
        socketReader.open = true;

        streamReader = new StreamReader();
    }

    /* DANIELE:
     * Method that verify if the client has data in the channel and in this case
     * call an executor to perform reading operations.
     */
    void run() {
        try {
            // Check if the socket is open
            if (socketReader.open) {
                // Verify semaphore and if there are data into the socket.
                if (!isReading && !isScheduled) {
                    try {
                        // Semaphore to avoid concurrent schedule of the same read operation.
                        isScheduled = true;
                        // Schedule execution with executor
                        IOExecutor.execute(streamReader);
                    }
                    catch (Exception e) {
                        if (socketReader.session != null) {
                            Log.warn(LocaleUtils.getLocalizedString("admin.error.stream") +
                                    ". Session: " +
                                    socketReader.session, e);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            socketReader.shutdown();
            // There is an exception...
            Log.error(e);
        }
        if (!socketReader.open) {
            socketReader.shutdown();
        }
    }

    protected void tlsNegotiated() throws XmlPullParserException, IOException {
        XmlPullParser xpp = socketReader.reader.getXPPParser();
        InputStream is = socketReader.connection.getTLSStreamHandler().getInputStream();
        xpp.setInput(new InputStreamReader(is, CHARSET));
        xmlLightWeightParser.setInput( is, CHARSET );
        super.tlsNegotiated();
    }

    protected boolean compressClient(Element doc) throws IOException, XmlPullParserException {
        boolean answer = super.compressClient(doc);
        if (answer) {
            XmlPullParser xpp = socketReader.reader.getXPPParser();
            // Reset the parser since a new stream header has been sent from the client
            if (socketReader.connection.getTLSStreamHandler() == null) {
                InputStream is;
                if (socketChannel != null) {
                    // DANIELE: Create an inputstream using the utility class ChannelInputStream.
                    is = new ChannelInputStream(socketChannel);
                }
                else {
                    is = socket.getInputStream();
                }
                is = ServerTrafficCounter.wrapInputStream(is);

                ZInputStream in = new ZInputStream(is);
                in.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
                xpp.setInput(new InputStreamReader(in, CHARSET));
            }
            else {
                ZInputStream in = new ZInputStream(
                        socketReader.connection.getTLSStreamHandler().getInputStream());
                in.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
                xpp.setInput(new InputStreamReader(in, CHARSET));
                xmlLightWeightParser.setInput( in, CHARSET );
            }
        }
        return answer;
    }

    class StreamReader implements Runnable {

        /*
         * This method is invoked when client send data to the channel.
         *
         */
        public void run() {
            try {
                // If no other reading operations are perform
                if (!isReading) {
                    // Change the semaphore status
                    isReading = true;
                    // Call the XML light-wieght parser to read data...
                    xmlLightWeightParser.read();
                    // Check if the parser has found a complete message...
                    if (xmlLightWeightParser.areThereMsgs()) {
                        // Process every message found
                        String[] msgs = xmlLightWeightParser.getMsgs();
                        for (int i = 0; i < msgs.length; i++) {
                            //System.out.println( "Processing " + msgs[ i ] );
                            readStream(msgs[i]);
                        }
                    }
                }
            }
            catch (IOException e) {
                if (socketReader.session != null) {
                    // DANIELE: Remove session from SessionManager. I don't know if
                    // this is the easy way.
                    // TODO Review this. Closing the connection should be used???
                    SessionManager.getInstance().removeSession(
                            SessionManager.getInstance().getSession(
                                    socketReader.session.getAddress()));
                }
                try {
                    xmlLightWeightParser.getChannel().close();
                }
                catch (IOException e1) {
                }
                // System.out.println( "Client disconnecting" );
            }
            catch (Exception e) {
                if (socketReader.session != null) {
                    Log.warn(LocaleUtils.getLocalizedString("admin.error.stream") + ". Session: " +
                            socketReader.session, e);
                }
                e.printStackTrace();
            }
            finally {
                isReading = false;
                isScheduled = false;
            }
        }

        /**
         * Process a single message
         */
        private void readStream(String msg) throws Exception {

            if (msg.trim().startsWith(STREAM_START)) {
                // Found an stream:stream tag...
                if (!sessionCreated) {
                    sessionCreated = true;
                    socketReader.reader.getXPPParser().setInput(new StringReader(
                            msg + ((msg.indexOf("</stream:stream") == -1) ? "</stream:stream>" :
                                    "")));
                    socketReader.createSession();
                }
                else if (awaytingSasl) {
                    awaytingSasl = false;
                    saslSuccessful();
                }
                else if (awaitingForCompleteCompression) {
                    awaitingForCompleteCompression = false;
                    compressionSuccessful();
                }
                return;
            }

            // Create dom in base on the string.
            Element doc = socketReader.reader.parseDocument(msg).getRootElement();
            if (doc == null) {
                // No document found.
                return;
            }
            String tag = doc.getName();
            if ("starttls".equals(tag)) {
                // Negotiate TLS
                if (negotiateTLS()) {
                    tlsNegotiated();
                }
                else {
                    socketReader.open = false;
                    socketReader.session = null;
                }
            }
            else if ("auth".equals(tag)) {
                // User is trying to authenticate using SASL
                if (authenticateClient(doc)) {
                    // SASL authentication was successful so open a new stream and offer
                    // resource binding and session establishment (to client sessions only)
                    awaytingSasl = true;
                }
                else if (socketReader.connection.isClosed()) {
                    socketReader.open = false;
                    socketReader.session = null;
                }
            }
            else if ("compress".equals(tag))
            {
                // Client is trying to initiate compression
                if (compressClient(doc)) {
                    // Compression was successful so open a new stream and offer
                    // resource binding and session establishment (to client sessions only)
                    awaitingForCompleteCompression = true;
                }
            }
            else {
                socketReader.process(doc);
            }
        }
    }
}
